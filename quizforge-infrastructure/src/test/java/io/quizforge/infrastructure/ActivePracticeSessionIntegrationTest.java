package io.quizforge.infrastructure;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.PracticeTransaction;
import io.quizforge.core.practice.ActivePracticeSnapshot;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeQuestionSnapshotMapper;
import io.quizforge.core.practice.PracticeSession;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.PracticeSessionService;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.question.type.objective.choice.QuestionText;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionQuestionRepository;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import io.quizforge.infrastructure.persistence.practice.SqliteQuestionAttemptRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivePracticeSessionIntegrationTest {
    private static final Instant START = Instant.parse("2026-09-28T04:00:00.123456789Z");
    private static final Instant REOPEN = START.plusSeconds(60);
    private static final String DOC_REVISION = "qfd:v2:" + "b".repeat(64);
    @TempDir Path temporaryDirectory;
    private QuizForgeDataDirectory directory;
    private SqliteDatabase database;
    private SqlitePracticeSessionRepository sessions;
    private SqlitePracticeSessionQuestionRepository questions;
    private SqliteQuestionAttemptRepository attempts;
    private PracticeSessionService service;
    private final QuestionBankV2Codec codec = new QuestionBankV2Codec();
    private final PracticeQuestionSnapshotMapper mapper = new PracticeQuestionSnapshotMapper();

    @BeforeEach void setUp() {
        directory = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        database = new SqliteDatabase(directory);
        sessions = new SqlitePracticeSessionRepository(database);
        questions = new SqlitePracticeSessionQuestionRepository(database);
        attempts = new SqliteQuestionAttemptRepository(database);
        service = service(database, START);
    }

    @Test void firstOpenCreatesEntireRoundWithOrderedCompleteSnapshotsAndNoAttempts() throws Exception {
        var bank = bank(3);
        var opened = open(bank);
        assertEquals(bank.assetId(), opened.session().questionBankAssetId());
        assertEquals(codec.contentId(bank), opened.session().questionBankContentId());
        assertEquals(bank.title(), opened.session().bankTitleSnapshot());
        assertEquals(PracticeSession.Status.ACTIVE, opened.session().status());
        assertEquals(PracticeSession.View.QUESTION, opened.session().currentView());
        assertEquals("q_1", opened.session().currentQuestionId());
        assertEquals(START, opened.session().startedAt());
        assertEquals(List.of("q_1", "q_2", "q_3"), ids(opened));
        for (int index = 0; index < opened.questions().size(); index++) {
            var row = opened.questions().get(index);
            assertEquals(index, row.sessionQuestion().questionOrder());
            assertEquals(mapper.map(bank.questions().get(index)), row.sessionQuestion().snapshot());
            assertEquals(PracticeSessionQuestion.State.UNANSWERED, row.sessionQuestion().practiceState());
            assertNull(row.sessionQuestion().draftAnswer());
            assertTrue(row.attempts().isEmpty());
        }
        assertEquals(1, count("practice_session"));
        assertEquals(3, count("practice_session_question"));
        assertEquals(0, count("question_attempt"));
    }

    @Test void repeatedOpenRestoresSameSessionWithoutCreatingAnotherActive() throws Exception {
        var first = open(bank(3));
        var second = open(bank(3));
        assertEquals(first, second);
        assertEquals(1, count("practice_session"));
        assertEquals(3, count("practice_session_question"));
    }

    @Test void restoresFromReopenedDatabaseServiceWithoutAnyMemoryCache() {
        var bank = bank(3);
        var first = open(bank);
        seedAnswer(first, "q_2");
        questions.updateDraft(first.session().id(), "q_1", answer(1), PracticeSessionQuestion.State.DRAFT, START.plusSeconds(1));
        seedAnswer(first, "q_3");
        questions.updateState(first.session().id(), "q_3", PracticeSessionQuestion.State.SUBMITTED, START.plusSeconds(1));
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.QUESTION, "q_2");
        var before = persisted(first.session().id());
        service = null;
        var reopened = service(new SqliteDatabase(directory), REOPEN)
                .openOrCreateActiveSession(bank, codec.contentId(bank));
        assertEquals(before.session().id(), reopened.session().id());
        assertEquals("q_2", reopened.session().currentQuestionId());
        assertEquals(PracticeSession.View.QUESTION, reopened.session().currentView());
        assertEquals(before.questions(), reopened.questions());
        assertEquals(REOPEN, reopened.session().lastActivityAt());
        assertEquals(START, reopened.session().startedAt());
    }

    @Test void equalRevisionDoesNotUpdateAnyQuestionColumnsOrAttempts() throws Exception {
        var bank = bank(3);
        var first = open(bank);
        seedAnswer(first, "q_1");
        var before = persisted(first.session().id());
        sql("""
                CREATE TRIGGER reject_question_rewrite BEFORE UPDATE ON practice_session_question
                BEGIN SELECT RAISE(ABORT, 'unchanged revision must not update questions'); END
                """);
        var restored = service(database, REOPEN).openOrCreateActiveSession(bank, codec.contentId(bank));
        assertEquals(before.questions(), restored.questions());
        assertEquals(REOPEN, restored.session().lastActivityAt());
    }

    @Test void addsNewQuestionWithoutChangingExistingDraftsOrAttempts() {
        var first = open(bank(2));
        seedAnswer(first, "q_1");
        var previous = question(persisted(first.session().id()), "q_1");
        var changed = open(bank(3));
        assertEquals(first.session().id(), changed.session().id());
        assertEquals(previous, question(changed, "q_1"));
        var added = question(changed, "q_3");
        assertEquals(PracticeSessionQuestion.State.UNANSWERED, added.sessionQuestion().practiceState());
        assertNull(added.sessionQuestion().draftAnswer());
        assertTrue(added.attempts().isEmpty());
        assertEquals(2, added.sessionQuestion().questionOrder());
    }

    @Test void removesDeletedQuestionAndOnlyItsCurrentRoundAttempts() {
        var bank = bank(3);
        var first = open(bank);
        seedAnswer(first, "q_1");
        seedAnswer(first, "q_2");
        var before = persisted(first.session().id());
        var deleted = question(before, "q_2");
        var changed = open(copy(bank, List.of(bank.questions().get(0), bank.questions().get(2))));
        assertEquals(List.of("q_1", "q_3"), ids(changed));
        assertTrue(attempts.listBySessionQuestion(deleted.sessionQuestion().id()).isEmpty());
        assertEquals(question(before, "q_1"), question(changed, "q_1"));
    }

    @ParameterizedTest(name = "semantic change: {0}")
    @ValueSource(strings = {"stem", "option_add", "option_remove", "option_id", "option_text", "option_order", "correct", "type"})
    void semanticChangesResetOnlyAffectedQuestion(String change) {
        var bank = bank(2);
        var first = open(bank);
        seedAnswer(first, "q_1");
        seedAnswer(first, "q_2");
        var before = persisted(first.session().id());
        var changedEntry = semanticChange(bank.questions().getFirst(), change);
        var changedBank = copy(bank, List.of(changedEntry, bank.questions().get(1)));
        var changed = service(database, REOPEN).openOrCreateActiveSession(changedBank, codec.contentId(changedBank));
        var reset = question(changed, "q_1");
        assertEquals(mapper.map(changedEntry), reset.sessionQuestion().snapshot());
        assertEquals(PracticeSessionQuestion.State.UNANSWERED, reset.sessionQuestion().practiceState());
        assertNull(reset.sessionQuestion().draftAnswer());
        assertTrue(reset.attempts().isEmpty());
        assertEquals(question(before, "q_1").sessionQuestion().id(), reset.sessionQuestion().id());
        assertEquals(question(before, "q_1").sessionQuestion().createdAt(), reset.sessionQuestion().createdAt());
        assertEquals(question(before, "q_2"), question(changed, "q_2"));
        assertEquals(codec.contentId(changedBank), changed.session().questionBankContentId());
    }

    @ParameterizedTest(name = "non-semantic change: {0}")
    @ValueSource(strings = {"analysis", "sourceRefs"})
    void nonSemanticChangesUpdateSnapshotAndKeepUserWork(String change) {
        var bank = bank(2);
        var first = open(bank);
        seedAnswer(first, "q_1");
        var previous = question(persisted(first.session().id()), "q_1");
        var entry = nonSemanticChange(bank.questions().getFirst(), change);
        var changedBank = copy(bank, List.of(entry, bank.questions().get(1)));
        var changed = service(database, REOPEN).openOrCreateActiveSession(changedBank, codec.contentId(changedBank));
        var updated = question(changed, "q_1");
        assertEquals(mapper.map(entry), updated.sessionQuestion().snapshot());
        assertEquals(previous.sessionQuestion().draftAnswer(), updated.sessionQuestion().draftAnswer());
        assertEquals(previous.sessionQuestion().practiceState(), updated.sessionQuestion().practiceState());
        assertEquals(previous.attempts(), updated.attempts());
        assertEquals(previous.sessionQuestion().id(), updated.sessionQuestion().id());
        assertEquals(REOPEN, updated.sessionQuestion().updatedAt());
    }

    @Test void reorderOnlyUpdatesGlobalOrderWithoutRewritingSnapshotsOrResettingUserWork() throws Exception {
        var bank = bank(3);
        var first = open(bank);
        seedAnswer(first, "q_1");
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.QUESTION, "q_2");
        var before = persisted(first.session().id());
        sql("""
                CREATE TRIGGER reject_snapshot_rewrite BEFORE UPDATE OF question_type, stem_snapshot,
                    options_snapshot_json, correct_answer_snapshot_json, analysis_snapshot, source_refs_snapshot_json
                ON practice_session_question
                BEGIN SELECT RAISE(ABORT, 'reorder must not rewrite snapshot'); END
                """);
        var changedBank = copy(bank, List.of(bank.questions().get(2), bank.questions().get(0), bank.questions().get(1)));
        var changed = open(changedBank);
        assertEquals(List.of("q_3", "q_1", "q_2"), ids(changed));
        for (int index = 0; index < changed.questions().size(); index++) {
            var current = changed.questions().get(index);
            var old = question(before, current.sessionQuestion().questionId());
            assertEquals(index, current.sessionQuestion().questionOrder());
            assertEquals(old.sessionQuestion().snapshot(), current.sessionQuestion().snapshot());
            assertEquals(old.sessionQuestion().draftAnswer(), current.sessionQuestion().draftAnswer());
            assertEquals(old.sessionQuestion().practiceState(), current.sessionQuestion().practiceState());
            assertEquals(old.attempts(), current.attempts());
        }
        assertEquals("q_2", changed.session().currentQuestionId());
    }

    @Test void titleChangeAdvancesBankMetadataWithoutRewritingQuestionSnapshots() {
        var bank = bank(2);
        var first = open(bank);
        seedAnswer(first, "q_1");
        var before = persisted(first.session().id());
        var renamed = new QuestionBank(bank.assetId(), "新题库标题", "2.0", List.of(), bank.questions(), List.of());
        var changed = service(database, REOPEN).openOrCreateActiveSession(renamed, codec.contentId(renamed));
        assertEquals(before.questions(), changed.questions());
        assertEquals("新题库标题", changed.session().bankTitleSnapshot());
        assertEquals(codec.contentId(renamed), changed.session().questionBankContentId());
        assertEquals(REOPEN, changed.session().lastActivityAt());
        assertEquals(first.session().id(), changed.session().id());
    }

    @Test void deletedMiddleCurrentMovesToNextSurvivingQuestion() {
        var bank = bank(4);
        var first = open(bank);
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.QUESTION, "q_3");
        var changed = open(copy(bank, List.of(bank.questions().get(0), bank.questions().get(1), bank.questions().get(3))));
        assertEquals("q_4", changed.session().currentQuestionId());
        assertTrue(ids(changed).contains(changed.session().currentQuestionId()));
    }

    @Test void deletedLastCurrentMovesToNearestSurvivingPreviousQuestion() {
        var bank = bank(3);
        var first = open(bank);
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.QUESTION, "q_3");
        var changed = open(copy(bank, bank.questions().subList(0, 2)));
        assertEquals("q_2", changed.session().currentQuestionId());
    }

    @Test void multipleDeletionsUseNearestOldSuccessorEvenWhenNewOrderChanges() {
        var bank = bank(5);
        var first = open(bank);
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.QUESTION, "q_3");
        var changed = open(copy(bank, List.of(bank.questions().get(4), bank.questions().get(0))));
        assertEquals("q_5", changed.session().currentQuestionId());
    }

    @Test void replacingEntireQuestionSetChoosesFirstNewQuestion() {
        var first = open(bank(2));
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.QUESTION, "q_2");
        var replacement = copy(bank(2), List.of(entry(8), entry(9)));
        var changed = open(replacement);
        assertEquals(List.of("q_8", "q_9"), ids(changed));
        assertEquals("q_8", changed.session().currentQuestionId());
    }

    @Test void summaryRestoresAfterDatabaseReopenAtSameRevision() {
        var bank = bank(2);
        var first = open(bank);
        seedAnswer(first, "q_1");
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.SUMMARY, null);
        var before = persisted(first.session().id());
        var restored = service(new SqliteDatabase(directory), REOPEN).openOrCreateActiveSession(bank, codec.contentId(bank));
        assertEquals(PracticeSession.View.SUMMARY, restored.session().currentView());
        assertNull(restored.session().currentQuestionId());
        assertEquals(before.questions(), restored.questions());
    }

    @ParameterizedTest(name = "SUMMARY remains: {0}")
    @ValueSource(strings = {"analysis", "sourceRefs", "reorder"})
    void summarySurvivesNonSemanticRevisionChange(String change) {
        var bank = bank(3);
        var first = open(bank);
        seedAnswer(first, "q_1");
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.SUMMARY, null);
        var originalAttempts = question(persisted(first.session().id()), "q_1").attempts();
        var changedBank = change.equals("reorder") ? copy(bank, bank.questions().reversed())
                : copy(bank, List.of(nonSemanticChange(bank.questions().getFirst(), change), bank.questions().get(1), bank.questions().get(2)));
        var changed = open(changedBank);
        assertEquals(PracticeSession.View.SUMMARY, changed.session().currentView());
        assertNull(changed.session().currentQuestionId());
        assertEquals(originalAttempts, question(changed, "q_1").attempts());
    }

    @ParameterizedTest(name = "SUMMARY returns to QUESTION: {0}")
    @ValueSource(strings = {"add", "delete", "reset"})
    void summaryReturnsToValidQuestionForStructuralOrSemanticChanges(String change) {
        var bank = bank(3);
        var first = open(bank);
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.SUMMARY, null);
        var changedBank = switch (change) {
            case "add" -> bank(4);
            case "delete" -> copy(bank, bank.questions().subList(1, 3));
            case "reset" -> copy(bank, List.of(semanticChange(bank.questions().getFirst(), "stem"),
                    bank.questions().get(1), bank.questions().get(2)));
            default -> throw new IllegalArgumentException(change);
        };
        var changed = open(changedBank);
        assertEquals(PracticeSession.View.QUESTION, changed.session().currentView());
        assertEquals(changedBank.questions().getFirst().id(), changed.session().currentQuestionId());
    }

    @Test void archivedRoundsStayFrozenWhileActiveRoundSynchronizes() {
        var bank = bank(3);
        var archived = open(bank);
        seedAnswer(archived, "q_1");
        sessions.archive(archived.session().id(), START.plusSeconds(1));
        var frozen = persisted(archived.session().id());
        var active = open(bank);
        seedAnswer(active, "q_1");
        var changedBank = new QuestionBank(bank.assetId(), "修改后的题库", "2.0", List.of(), List.of(semanticChange(bank.questions().getFirst(), "stem"), entry(4)), List.of());
        var changed = open(changedBank);
        assertEquals(active.session().id(), changed.session().id());
        assertNotEquals(archived.session().id(), changed.session().id());
        assertEquals(frozen, persisted(archived.session().id()));
        assertEquals(List.of("q_1", "q_4"), ids(changed));
        assertTrue(question(changed, "q_1").attempts().isEmpty());
    }

    @Test void failedInitialQuestionBatchRollsBackSessionAndEveryInsertedQuestion() throws Exception {
        sql("""
                CREATE TRIGGER fail_second_question BEFORE INSERT ON practice_session_question
                WHEN NEW.question_id = 'q_2'
                BEGIN SELECT RAISE(ABORT, 'controlled create failure'); END
                """);
        assertPersistenceFailure(() -> open(bank(3)));
        assertEquals(0, count("practice_session"));
        assertEquals(0, count("practice_session_question"));
        assertEquals(0, count("question_attempt"));
    }

    @Test void syncFailureRollsBackDeletedQuestionsResetAttemptsNewRowsPositionAndMetadata() throws Exception {
        var bank = bank(4);
        var first = open(bank);
        seedAnswer(first, "q_1");
        seedAnswer(first, "q_2");
        seedAnswer(first, "q_4");
        sessions.updateCurrentPosition(first.session().id(), PracticeSession.View.QUESTION, "q_4");
        var before = persisted(first.session().id());
        sql("""
                CREATE TRIGGER fail_sync_metadata BEFORE UPDATE OF question_bank_content_id ON practice_session
                BEGIN SELECT RAISE(ABORT, 'controlled late sync failure'); END
                """);
        var changedBank = copy(bank, List.of(entry(5), semanticChange(bank.questions().getFirst(), "stem"),
                nonSemanticChange(bank.questions().get(2), "analysis")));
        assertPersistenceFailure(() -> service(database, REOPEN).openOrCreateActiveSession(changedBank, codec.contentId(changedBank)));
        assertEquals(before, persisted(first.session().id()));
        assertEquals(1, count("practice_session"));
        assertEquals(4, count("practice_session_question"));
        assertEquals(3, count("question_attempt"));
        assertTrue(questions.findBySessionIdAndQuestionId(first.session().id(), "q_5").isEmpty());
    }

    @Test void errorAlsoRollsBackAllThreeRepositoriesAndLeakedScopeCannotBeUsed() throws Exception {
        PracticeTransaction tx = new SqlitePracticeTransaction(database);
        var initial = open(bank(1));
        var question = initial.questions().getFirst().sessionQuestion();
        var leaked = new PracticeTransaction.Repositories[1];
        assertThrows(AssertionError.class, () -> tx.execute(repositories -> {
            leaked[0] = repositories;
            repositories.sessions().touch(initial.session().id(), REOPEN);
            repositories.questions().updateDraft(initial.session().id(), "q_1", answer(1), PracticeSessionQuestion.State.DRAFT, REOPEN);
            repositories.attempts().append(new QuestionAttempt("attempt_rollback", question.id(), 1,
                    QuestionAttempt.Mode.INITIAL, answer(1), QuestionAttempt.Result.CORRECT, null, null, REOPEN));
            throw new AssertionError("controlled callback failure");
        }));
        assertEquals(initial, persisted(initial.session().id()));
        assertEquals(0, count("question_attempt"));
        assertPersistenceFailure(() -> leaked[0].sessions().findById(initial.session().id()));
    }

    @Test void concurrentOpenFromSeparateServicesCreatesOnlyOneActiveRound() throws Exception {
        var bank = bank(3);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var operation = (java.util.concurrent.Callable<ActivePracticeSnapshot>) () -> {
                var independent = service(database, START);
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return independent.openOrCreateActiveSession(bank, codec.contentId(bank));
            };
            var first = executor.submit(operation);
            var second = executor.submit(operation);
            try { assertTrue(ready.await(10, TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, count("practice_session"));
        assertEquals(3, count("practice_session_question"));
    }

    @Test void emptyBankKeepsExistingRejectionRuleWithoutDeletingActiveUserData() {
        var bank = bank(2);
        var first = open(bank);
        seedAnswer(first, "q_1");
        var before = persisted(first.session().id());
        var empty = copy(bank, List.of());
        var failure = assertThrows(QuizForgeException.class,
                () -> service.openOrCreateActiveSession(empty, "qfb:v2:" + "0".repeat(64)));
        assertEquals(ErrorCode.QUESTION_BANK_FILE_INVALID, failure.code());
        assertEquals(before, persisted(first.session().id()));
    }

    @Test void v2BankCanCreateAndRestoreSnapshots() {
        var bank = bank(1);
        var reread = codec.parse(codec.write(bank));
        var first = open(reread);
        seedAnswer(first, "q_1");
        var before = persisted(first.session().id());
        var restored = service(new SqliteDatabase(directory), REOPEN).openOrCreateActiveSession(reread, codec.contentId(reread));
        assertEquals(before.questions(), restored.questions());
        assertEquals(mapper.map(reread.questions().getFirst()), restored.questions().getFirst().sessionQuestion().snapshot());
    }

    @Test void independentBanksCreateIndependentActiveSessions() throws Exception {
        var bank = bank(1);
        var other = new QuestionBank("qb_other", "另一个题库", "2.0", List.of(), bank.questions(), List.of());
        var first = open(bank);
        var second = open(other);
        assertNotEquals(first.session().id(), second.session().id());
        assertEquals(first.session().id(), open(bank).session().id());
        assertEquals(second.session().id(), open(other).session().id());
        assertEquals(2, count("practice_session"));
    }

    @Test void correctOptionIdPermutationChangesRevisionButDoesNotResetAnswerMeaning() {
        var bank = bank(2);
        var multiple = semanticChange(bank.questions().getFirst(), "type");
        var initialBank = copy(bank, List.of(multiple, bank.questions().get(1)));
        var first = open(initialBank);
        seedAnswer(first, "q_1");
        var before = persisted(first.session().id());
        var reorderedAnswers = Question.choice(multiple.id(), multiple.type(), multiple.prompt(), multiple.analysis(), multiple.sourceRefs(), new ChoicePayload(multiple.choicePayload().options()), new ChoiceAnswerSpec(multiple.choiceAnswerSpec().correctOptionIds().reversed()));
        var changedBank = copy(initialBank, List.of(reorderedAnswers, bank.questions().get(1)));
        assertNotEquals(codec.contentId(initialBank), codec.contentId(changedBank));
        var changed = open(changedBank);
        assertEquals(before.questions(), changed.questions());
        assertEquals(codec.contentId(changedBank), changed.session().questionBankContentId());
    }

    @Test void invalidRevisionIsRejectedBeforeCreatingAnything() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.openOrCreateActiveSession(bank(1), "path/to/bank.qbank"));
        assertEquals(0, count("practice_session"));
    }

    private PracticeSessionService service(SqliteDatabase database, Instant instant) {
        return new PracticeSessionService(new SqlitePracticeTransaction(database), Clock.fixed(instant, ZoneOffset.UTC));
    }

    private ActivePracticeSnapshot open(QuestionBank bank) {
        return service.openOrCreateActiveSession(bank, codec.contentId(bank));
    }

    private ActivePracticeSnapshot persisted(String sessionId) {
        var session = sessions.findById(sessionId).orElseThrow();
        return new ActivePracticeSnapshot(session, questions.findBySessionId(sessionId).stream()
                .map(question -> new ActivePracticeSnapshot.Question(question, attempts.listBySessionQuestion(question.id()))).toList());
    }

    private ActivePracticeSnapshot.Question question(ActivePracticeSnapshot snapshot, String questionId) {
        return snapshot.questions().stream().filter(row -> row.sessionQuestion().questionId().equals(questionId)).findFirst().orElseThrow();
    }

    private List<String> ids(ActivePracticeSnapshot snapshot) {
        return snapshot.questions().stream().map(row -> row.sessionQuestion().questionId()).toList();
    }

    private void seedAnswer(ActivePracticeSnapshot snapshot, String questionId) {
        var row = question(snapshot, questionId).sessionQuestion();
        var draft = answer(Integer.parseInt(questionId.substring(2)));
        questions.updateDraft(snapshot.session().id(), questionId, draft, PracticeSessionQuestion.State.RETRYING, START.plusSeconds(1));
        attempts.append(new QuestionAttempt("attempt_" + row.id(), row.id(), 1,
                QuestionAttempt.Mode.INITIAL, draft, QuestionAttempt.Result.CORRECT, null, null, START.plusSeconds(1)));
    }

    private PracticePayload answer(int number) {
        return new PracticePayload(Map.of("selectedOptionIds", List.of("opt_" + number + "_a")));
    }

    private QuestionBank bank(int count) {
        return new QuestionBank("qb_practice", "Java练习", "2.0", List.of(), IntStream.rangeClosed(1, count).mapToObj(this::entry).toList(), List.of());
    }

    private Question entry(int index) {
        return Question.choice("q_" + index, "SINGLE_CHOICE", new TextContent("问题 " + index), new TextContent("解析 " + index), List.of(SourceRef.anchor("doc_java", DOC_REVISION, "来源 " + index, 1, "Java", "来源 " + index)), new ChoicePayload(List.of(new ChoiceOption("opt_" + index + "_a", new TextContent("数组")),
                        new ChoiceOption("opt_" + index + "_b", new TextContent("链表")),
                        new ChoiceOption("opt_" + index + "_c", new TextContent("树")))), new ChoiceAnswerSpec(List.of("opt_" + index + "_a")));
    }

    private QuestionBank copy(QuestionBank bank, List<Question> entries) {
        return new QuestionBank(bank.assetId(), bank.title(), "2.0", List.of(), entries, List.of());
    }

    private Question semanticChange(Question entry, String change) {
        String type = entry.type();
        String stem = QuestionText.prompt(entry);
        var options = new ArrayList<>(entry.choicePayload().options());
        var correct = entry.choiceAnswerSpec().correctOptionIds();
        switch (change) {
            case "stem" -> stem += "（已修改）";
            case "option_add" -> options.add(new ChoiceOption("opt_extra", new TextContent("新增选项")));
            case "option_remove" -> options.removeLast();
            case "option_id" -> options.set(1, new ChoiceOption("opt_changed", options.get(1).content()));
            case "option_text" -> options.set(1, new ChoiceOption(options.get(1).id(), new TextContent("新选项文本")));
            case "option_order" -> options = new ArrayList<>(options.reversed());
            case "correct" -> correct = List.of(options.get(1).id());
            case "type" -> { type = "MULTIPLE_CHOICE"; correct = List.of(options.get(0).id(), options.get(1).id()); }
            default -> throw new IllegalArgumentException(change);
        }
        return Question.choice(entry.id(), type, new TextContent(stem), entry.analysis(), entry.sourceRefs(), new ChoicePayload(options), new ChoiceAnswerSpec(correct));
    }

    private Question nonSemanticChange(Question entry, String change) {
        String analysis = change.equals("analysis") ? "新解析" : QuestionText.analysis(entry);
        var refs = change.equals("sourceRefs")
                ? List.of(SourceRef.anchor("doc_java", DOC_REVISION, "新来源", 2, "Java", "新来源"))
                : entry.sourceRefs();
        return Question.choice(entry.id(), entry.type(), entry.prompt(), new TextContent(analysis), refs, entry.choicePayload(), entry.choiceAnswerSpec());
    }

    private void sql(String sql) throws Exception {
        try (Connection connection = database.openConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(String table) throws Exception {
        try (Connection connection = database.openConnection(); Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT count(*) FROM " + table)) {
            assertTrue(row.next());
            return row.getLong(1);
        }
    }

    private void assertPersistenceFailure(Runnable operation) {
        var failure = assertThrows(QuizForgeException.class, operation::run);
        assertEquals(ErrorCode.PERSISTENCE_FAILED, failure.code());
    }
}
