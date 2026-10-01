package io.quizforge.infrastructure;

import io.quizforge.core.practice.EssayPracticeAnswer;
import io.quizforge.core.practice.EssayQuestionSnapshot;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticeHistoryEntry;
import io.quizforge.core.practice.PracticeHistoryService;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeQuestionSnapshotMapper;
import io.quizforge.core.practice.PracticeSession;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.PracticeSessionService;
import io.quizforge.core.practice.PracticeSummary;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.core.practice.QuestionBankPracticeSession;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.EvaluationCriterion;
import io.quizforge.core.question.model.EvaluationSpec;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.source.QuestionSourceDocument;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.question.type.objective.choice.QuestionText;
import io.quizforge.core.question.type.subjective.essay.EssayAnswerSpec;
import io.quizforge.core.question.type.subjective.essay.EssayPayload;
import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionQuestionRepository;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import io.quizforge.infrastructure.persistence.practice.SqliteQuestionAttemptRepository;
import io.quizforge.infrastructure.persistence.practice.SqliteWorkspacePracticeRuntimeProvider;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PersistentPracticeRuntimeIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-28T05:00:00Z");
    @TempDir Path temp;
    private SqliteDatabase database;
    private PracticeSessionService service;
    private PersistentPracticeRuntime runtime;
    private QuestionBank bank;
    private final QuestionBankV2Codec codec = new QuestionBankV2Codec();

    @Test void styledEssaySurvivesSqliteReloadAndHistoryWithoutEnumLoss() {
        var prompt=new io.quizforge.core.question.content.RichContent(new io.quizforge.core.question.content.RichDocument(List.of(
                new io.quizforge.core.question.content.HeadingNode(2,List.of(new io.quizforge.core.question.content.InlineTextNode("Styled title",
                        List.of(io.quizforge.core.question.content.TextMark.BOLD))),io.quizforge.core.question.content.TextAlignment.CENTER),
                new io.quizforge.core.question.content.ParagraphNode(List.of(new io.quizforge.core.question.content.InlineTextNode("Styled body",
                        List.of(io.quizforge.core.question.content.TextMark.ITALIC))),io.quizforge.core.question.content.TextAlignment.RIGHT))));
        var question=new Question("q_styled","ESSAY",List.of(),prompt,new EssayPayload(null),new EssayAnswerSpec(prompt),
                ScoreSpec.defaultScore(),null,null,List.of());
        var styled=new QuestionBank("qb_styled","Styled",List.of(),List.of(question),List.of());
        var active=new PersistentPracticeRuntime(service,styled,codec.contentId(styled));
        active.saveEssayDraft(question.id(),new EssayPracticeAnswer("Answer",null));active.submit();
        var restored=new PersistentPracticeRuntime(service(new SqliteDatabase(new QuizForgeDataDirectory(temp))),styled,codec.contentId(styled));
        assertEquals(1,restored.questionState(question.id()).attempts().size());
        String archived=restored.sessionId();restored.restart();
        var detail=new PracticeHistoryService(new SqlitePracticeTransaction(database)).loadArchivedSessionDetail(styled.assetId(),archived);
        var fields=QuestionContentData.map(detail.questions().getFirst().contentSnapshot().value());
        var snapshot=EssayQuestionSnapshot.from(new PracticePayload(fields.get("essayPresentation")));
        assertEquals(prompt,snapshot.prompt());assertEquals(prompt,snapshot.reference());
        assertEquals(QuestionAttempt.Result.UNSCORED,detail.questions().getFirst().attempts().getFirst().result());
    }

    @BeforeEach void setup() {
        database = new SqliteDatabase(new QuizForgeDataDirectory(temp));
        service = service(database);
        bank = bank();
        runtime = new PersistentPracticeRuntime(service, bank, codec.contentId(bank));
    }

    @Test void firstOpenHydratesFirstUnansweredQuestionWithoutAttempts() {
        assertEquals(0, runtime.session().index());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, runtime.session().state());
        assertEquals(PracticeSession.Status.ACTIVE, session().status());
        assertEquals(0, attempts("q_one").size());
    }

    @Test void singleDraftSavesStableIdsAndReplacementWithoutAttempts() {
        runtime.select("opt_b");
        assertEquals(new PracticePayload(List.of("opt_b")), question("q_one").draftAnswer());
        runtime.select("opt_a");
        assertEquals(new PracticePayload(List.of("opt_a")), question("q_one").draftAnswer());
        assertEquals(PracticeSessionQuestion.State.DRAFT, question("q_one").practiceState());
        assertTrue(attempts("q_one").isEmpty());
    }

    @Test void multipleDraftAddsRemovesAndClearsToUnanswered() {
        runtime.next(); runtime.select("opt_d"); runtime.select("opt_e");
        assertEquals(Set.of("opt_d", "opt_e"), runtime.session().selected());
        runtime.select("opt_d");
        assertEquals(new PracticePayload(List.of("opt_e")), question("q_two").draftAnswer());
        runtime.select("opt_e");
        assertNull(question("q_two").draftAnswer());
        assertEquals(PracticeSessionQuestion.State.UNANSWERED, question("q_two").practiceState());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, runtime.session().state());
        assertTrue(attempts("q_two").isEmpty());
    }

    @Test void nextPreviousAndOutlineTargetUseOnePersistedPositionPathWithoutSubmitting() {
        runtime.select("opt_b"); runtime.next();
        assertEquals("q_two", session().currentQuestionId());
        runtime.previous();
        assertEquals("q_one", session().currentQuestionId());
        assertEquals(Set.of("opt_b"), runtime.session().selected());
        runtime.goTo(1);
        assertEquals("q_two", session().currentQuestionId());
        assertTrue(attempts("q_one").isEmpty());
        assertEquals(PracticeSessionQuestion.State.DRAFT, question("q_one").practiceState());
    }

    @ParameterizedTest @ValueSource(strings = {"opt_a", "opt_b"})
    void submitWritesOneInitialScoredAttemptAndClearsDraft(String option) {
        runtime.select(option); runtime.submit();
        var attempt = attempts("q_one").getFirst();
        assertEquals(1, attempt.attemptNo());
        assertEquals(QuestionAttempt.Mode.INITIAL, attempt.attemptMode());
        assertEquals(new PracticePayload(List.of(option)), attempt.answer());
        assertEquals(option.equals("opt_a") ? QuestionAttempt.Result.CORRECT : QuestionAttempt.Result.INCORRECT, attempt.result());
        assertEquals(PracticeSessionQuestion.State.SUBMITTED, question("q_one").practiceState());
        assertNull(question("q_one").draftAnswer());
        assertEquals(option.equals("opt_a"), runtime.session().correct());
    }

    @Test void actualObjectRebuildRestoresCurrentDraftAndSubmittedAttemptWithoutMemoryState() {
        runtime.select("opt_b"); runtime.next(); runtime.select("opt_d"); runtime.select("opt_e"); runtime.submit();
        String id = runtime.sessionId();
        String fileText = codec.write(bank);
        runtime = null; service = null; database = null; bank = null;
        database = new SqliteDatabase(new QuizForgeDataDirectory(temp));
        service = service(database);
        bank = codec.parse(fileText);
        runtime = new PersistentPracticeRuntime(service, bank, codec.contentId(bank));
        assertEquals(id, runtime.sessionId());
        assertEquals(1, runtime.session().index());
        assertEquals(Set.of("opt_d", "opt_e"), runtime.session().selected());
        assertTrue(runtime.session().correct());
        assertEquals(QuestionBankPracticeSession.State.SELECTED, runtime.session().state(0));
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, runtime.session().state(1));
        runtime.previous();
        assertEquals(Set.of("opt_b"), runtime.session().selected());
        assertTrue(attempts("q_one").isEmpty());
        assertEquals(1, attempts("q_two").size());
    }

    @Test void submittedAnswerComesFromAttemptEvenWhenDraftIsNull() {
        runtime.select("opt_b"); runtime.submit();
        assertNull(question("q_one").draftAnswer());
        var restored = new PersistentPracticeRuntime(service(database), codec.parse(codec.write(bank)), codec.contentId(bank));
        assertEquals(Set.of("opt_b"), restored.session().selected());
        assertFalse(restored.session().correct());
    }

    @Test void inconsistentSubmittedRowWithoutAttemptFailsClosedDuringHydration() {
        new SqlitePracticeSessionQuestionRepository(database).updateState(runtime.sessionId(), "q_one",
                PracticeSessionQuestion.State.SUBMITTED, NOW);
        assertThrows(IllegalStateException.class,
                () -> new PersistentPracticeRuntime(service(database), bank, codec.contentId(bank)));
    }

    @Test void eachRuntimeCommandTouchesActivityInItsTransaction() {
        Instant later = NOW.plusSeconds(60);
        var commands = new PracticeSessionService(new SqlitePracticeTransaction(database), Clock.fixed(later, ZoneOffset.UTC));
        commands.saveDraft(runtime.sessionId(), codec.contentId(bank), "q_one", Set.of("opt_a"));
        assertEquals(later, session().lastActivityAt());
        commands.submitAnswer(runtime.sessionId(), codec.contentId(bank), "q_one");
        assertEquals(later, session().lastActivityAt());
        commands.updateCurrentQuestion(runtime.sessionId(), codec.contentId(bank), "q_two");
        assertEquals(later, session().lastActivityAt());
    }

    @Test void repeatedSubmitFailsClosedAndNeverAddsSecondInitial() {
        runtime.select("opt_a"); runtime.submit();
        assertThrows(IllegalStateException.class, runtime::submit);
        assertThrows(IllegalStateException.class, () -> service.submitAnswer(runtime.sessionId(), codec.contentId(bank), "q_one"));
        assertEquals(1, attempts("q_one").size());
    }

    @Test void concurrentConfirmCreatesExactlyOneInitialAttempt() throws Exception {
        runtime.select("opt_a");
        String id = runtime.sessionId(), revision = codec.contentId(bank);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> confirm = () -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                try { service(new SqliteDatabase(new QuizForgeDataDirectory(temp))).submitAnswer(id, revision, "q_one"); return true; }
                catch (IllegalStateException alreadySubmitted) { return false; }
            };
            var first = executor.submit(confirm); var second = executor.submit(confirm);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, attempts("q_one").size());
    }

    @ParameterizedTest @ValueSource(strings = {"question", "activity"})
    void failedSubmitRollsBackAttemptStateAndDraftAndDoesNotHydrateSuccess(String failurePoint) throws Exception {
        runtime.select("opt_b");
        var beforeQuestion = question("q_one"); var beforeSession = session();
        sql(failurePoint.equals("question")
                ? "CREATE TRIGGER fail_submit BEFORE UPDATE OF practice_state ON practice_session_question WHEN NEW.practice_state = 'SUBMITTED' BEGIN SELECT RAISE(ABORT, 'forced failure'); END"
                : "CREATE TRIGGER fail_submit BEFORE UPDATE OF last_activity_at ON practice_session BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        assertThrows(RuntimeException.class, runtime::submit);
        assertEquals(beforeQuestion, question("q_one"));
        assertEquals(beforeSession, session());
        assertTrue(attempts("q_one").isEmpty());
        assertEquals(QuestionBankPracticeSession.State.SELECTED, runtime.session().state());
        assertEquals(Set.of("opt_b"), runtime.session().selected());
    }

    @Test void failedDraftAndNavigationLeaveRuntimeAndPersistedStateUnchanged() throws Exception {
        runtime.select("opt_a");
        sql("CREATE TRIGGER fail_touch BEFORE UPDATE OF last_activity_at ON practice_session BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        assertThrows(RuntimeException.class, () -> runtime.select("opt_b"));
        assertThrows(RuntimeException.class, runtime::next);
        assertEquals(Set.of("opt_a"), runtime.session().selected());
        assertEquals(new PracticePayload(List.of("opt_a")), question("q_one").draftAnswer());
        assertEquals(0, runtime.session().index());
        assertEquals("q_one", session().currentQuestionId());
    }

    @Test void summaryPersistsAndRebuildHydratesExistingResultStatistics() {
        runtime.select("opt_b"); runtime.submit(); runtime.next();
        runtime.select("opt_d"); runtime.select("opt_e"); runtime.submit(); runtime.next();
        assertEquals(PracticeSession.View.SUMMARY, session().currentView());
        var restored = new PersistentPracticeRuntime(service(database), bank, codec.contentId(bank));
        assertTrue(restored.session().finished());
        assertEquals(new QuestionBankPracticeSession.Result(2, 1, 1, 50), restored.session().result());
    }

    @Test void retryClearsCurrentAnswerButPreservesInitialAttemptAcrossRebuild() {
        runtime.select("opt_b"); runtime.submit();
        var initial = attempts("q_one").getFirst();
        runtime.retry();
        assertEquals(PracticeSessionQuestion.State.RETRYING, question("q_one").practiceState());
        assertNull(question("q_one").draftAnswer());
        assertEquals(List.of(initial), attempts("q_one"));
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, runtime.session().state());
        var restored = new PersistentPracticeRuntime(service(database), bank, codec.contentId(bank));
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, restored.session().state());
        assertTrue(restored.session().selected().isEmpty());
        assertEquals(1, attempts("q_one").size());
        assertEquals(0, restored.summary().submittedCount());
        assertEquals(2, restored.summary().unfinishedCount());
    }

    @Test void retryDraftRemainsRetryingAndMultipleSubmissionsAppendImmutableFacts() {
        runtime.select("opt_b"); runtime.submit();
        var initial = attempts("q_one").getFirst();
        for (int attempt = 2; attempt <= 4; attempt++) {
            runtime.retry();
            runtime.select(attempt == 3 ? "opt_a" : "opt_b");
            assertEquals(PracticeSessionQuestion.State.RETRYING, question("q_one").practiceState());
            assertNotNull(question("q_one").draftAnswer());
            runtime.submit();
            var latest = attempts("q_one").getLast();
            assertEquals(attempt, latest.attemptNo());
            assertEquals(QuestionAttempt.Mode.RETRY, latest.attemptMode());
            assertEquals(attempt == 3 ? QuestionAttempt.Result.CORRECT : QuestionAttempt.Result.INCORRECT, latest.result());
            assertEquals(PracticeSessionQuestion.State.SUBMITTED, question("q_one").practiceState());
            assertNull(question("q_one").draftAnswer());
        }
        assertEquals(initial, attempts("q_one").getFirst());
        assertEquals(4, attempts("q_one").size());
        assertFalse(runtime.session().correct());
    }

    @Test void emptyRetryDraftKeepsRetryContextAndPreviousAttempts() {
        runtime.goTo(1); runtime.select("opt_d"); runtime.submit(); runtime.retry();
        runtime.select("opt_d"); runtime.select("opt_d");
        assertEquals(PracticeSessionQuestion.State.RETRYING, question("q_two").practiceState());
        assertNull(question("q_two").draftAnswer());
        assertEquals(1, attempts("q_two").size());
        assertThrows(IllegalStateException.class, runtime::submit);
    }

    @Test void invalidRetryAndRetryTransactionFailureLeaveFactsUntouched() throws Exception {
        assertThrows(IllegalStateException.class, runtime::retry);
        runtime.select("opt_b");
        assertThrows(IllegalStateException.class, runtime::retry);
        runtime.submit();
        var before = question("q_one"); var facts = attempts("q_one"); var activity = session().lastActivityAt();
        sql("CREATE TRIGGER fail_retry BEFORE UPDATE OF practice_state ON practice_session_question WHEN NEW.practice_state = 'RETRYING' BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        assertThrows(RuntimeException.class, runtime::retry);
        assertEquals(before, question("q_one")); assertEquals(facts, attempts("q_one"));
        assertEquals(activity, session().lastActivityAt());
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, runtime.session().state());
        sql("DROP TRIGGER fail_retry");
        runtime.retry();
        assertThrows(IllegalStateException.class, runtime::retry);
    }

    @Test void summaryCanBeEnteredWithoutSubmissionsAndRestoresWithoutCurrentOutlineQuestion() {
        runtime.goTo(1); runtime.next();
        assertEquals(PracticeSession.View.SUMMARY, session().currentView());
        assertEquals(PracticeSession.Status.ACTIVE, session().status());
        assertTrue(runtime.session().finished());
        assertEquals(new PracticeSummary(2, 0, 0, 0, 2, java.util.OptionalInt.empty()), runtime.summary());
        var restored = new PersistentPracticeRuntime(service(database), bank, codec.contentId(bank));
        assertTrue(restored.session().finished());
        assertTrue(restored.summary().accuracyPercent().isEmpty());
        restored.previous();
        assertEquals(PracticeSession.View.QUESTION, session().currentView());
        assertEquals("q_two", session().currentQuestionId());
        restored.next(); restored.goTo(0);
        assertEquals(PracticeSession.View.QUESTION, session().currentView());
        assertEquals("q_one", session().currentQuestionId());
    }

    @Test void summaryUsesLatestSubmittedResultAndTreatsRetryingAsUnfinished() {
        runtime.select("opt_b"); runtime.submit(); runtime.goTo(1); runtime.select("opt_d");
        runtime.select("opt_e"); runtime.submit(); runtime.next();
        assertEquals(new PracticeSummary(2, 2, 1, 1, 0, java.util.OptionalInt.of(50)), runtime.summary());
        runtime.goTo(0); runtime.retry(); runtime.goTo(1); runtime.next();
        assertEquals(new PracticeSummary(2, 1, 1, 0, 1, java.util.OptionalInt.of(100)), runtime.summary());
        runtime.goTo(0); runtime.select("opt_a"); runtime.submit(); runtime.goTo(1); runtime.next();
        assertEquals(new PracticeSummary(2, 2, 2, 0, 0, java.util.OptionalInt.of(100)), runtime.summary());
        assertEquals(2, attempts("q_one").size());
    }

    @Test void submittingLastQuestionDoesNotOpenSummaryUntilNext() {
        runtime.goTo(1); runtime.select("opt_d"); runtime.select("opt_e"); runtime.submit();
        assertEquals(PracticeSession.View.QUESTION, session().currentView());
        assertFalse(runtime.session().finished());
        runtime.next();
        assertEquals(PracticeSession.View.SUMMARY, session().currentView());
    }

    @Test void restartArchivesEntireOldRoundAndCreatesFreshActiveFromCurrentBank() {
        runtime.select("opt_b"); runtime.submit(); runtime.goTo(1); runtime.select("opt_d"); runtime.next();
        String oldId = runtime.sessionId();
        var oldSession = session();
        var oldRows = new SqlitePracticeSessionQuestionRepository(database).findBySessionId(oldId);
        var oldFacts = new SqliteQuestionAttemptRepository(database).listBySessionQuestion(oldRows.getFirst().id());
        runtime.restart();
        assertNotEquals(oldId, runtime.sessionId());
        assertEquals(PracticeSession.Status.ARCHIVED, new SqlitePracticeSessionRepository(database).findById(oldId).orElseThrow().status());
        assertEquals(NOW, new SqlitePracticeSessionRepository(database).findById(oldId).orElseThrow().archivedAt());
        assertEquals(oldRows, new SqlitePracticeSessionQuestionRepository(database).findBySessionId(oldId));
        assertEquals(oldFacts, new SqliteQuestionAttemptRepository(database).listBySessionQuestion(oldRows.getFirst().id()));
        assertEquals(PracticeSession.Status.ACTIVE, session().status());
        assertEquals(PracticeSession.View.QUESTION, session().currentView());
        assertEquals("q_one", session().currentQuestionId());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, runtime.session().state());
        assertEquals(0, runtime.session().index());
        assertTrue(attempts("q_one").isEmpty());
        assertEquals(oldSession.questionBankAssetId(), session().questionBankAssetId());
        assertEquals(codec.contentId(bank), session().questionBankContentId());
        assertEquals(1, new SqlitePracticeSessionRepository(database).listArchivedByQuestionBankAssetId(bank.assetId()).size());
    }

    @Test void restartUsesUpdatedBankSnapshotWithoutChangingArchivedQuestion() {
        runtime.select("opt_a"); runtime.submit();
        String oldId = runtime.sessionId();
        var oldRow = question("q_one");
        var first = bank.questions().getFirst();
        var changed = Question.choice(first.id(), first.type(), new TextContent(QuestionText.prompt(first) + " updated"), first.analysis(), first.sourceRefs(), first.choicePayload(), first.choiceAnswerSpec());
        var newer = new QuestionBank(bank.assetId(), bank.title(), "2.0", List.of(), List.of(changed, bank.questions().get(1)), List.of());
        var snapshot = service.restartPractice(oldId, codec.contentId(bank), newer, codec.contentId(newer));
        assertEquals(codec.contentId(newer), snapshot.session().questionBankContentId());
        assertEquals(QuestionText.prompt(changed), snapshot.questions().getFirst().sessionQuestion().snapshot().stem());
        assertEquals(oldRow, new SqlitePracticeSessionQuestionRepository(database)
                .findBySessionIdAndQuestionId(oldId, "q_one").orElseThrow());
        var reopened = new PersistentPracticeRuntime(service(database), newer, codec.contentId(newer));
        assertEquals(snapshot.session().id(), reopened.sessionId());
        assertThrows(IllegalStateException.class, runtime::retry);
    }

    @Test void failedNewSessionCreationRollsBackArchiveAndKeepsRuntime() throws Exception {
        runtime.select("opt_b"); runtime.submit();
        String oldId = runtime.sessionId(); var oldSession = session(); var oldRow = question("q_one");
        sql("CREATE TRIGGER fail_new_round BEFORE INSERT ON practice_session WHEN NEW.status = 'ACTIVE' BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        assertThrows(RuntimeException.class, runtime::restart);
        assertEquals(oldId, runtime.sessionId());
        assertEquals(oldSession, session());
        assertEquals(oldRow, question("q_one"));
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, runtime.session().state());
        assertTrue(new SqlitePracticeSessionRepository(database).listArchivedByQuestionBankAssetId(bank.assetId()).isEmpty());
        sql("DROP TRIGGER fail_new_round");
        runtime.restart();
        assertNotEquals(oldId, runtime.sessionId());
    }

    @Test void invalidAndIncompleteCommandsDoNotWriteAnything() {
        assertThrows(IllegalStateException.class, runtime::submit);
        assertThrows(IllegalArgumentException.class, () -> service.saveDraft(runtime.sessionId(), codec.contentId(bank), "q_one", Set.of("A")));
        assertThrows(IllegalArgumentException.class, () -> service.saveDraft(runtime.sessionId(), codec.contentId(bank), "q_one", Set.of("opt_a", "opt_b")));
        assertThrows(IllegalArgumentException.class, () -> service.updateCurrentQuestion(runtime.sessionId(), codec.contentId(bank), "missing"));
        assertEquals(PracticeSessionQuestion.State.UNANSWERED, question("q_one").practiceState());
        assertEquals("q_one", session().currentQuestionId());
        assertTrue(attempts("q_one").isEmpty());
    }

    @Test void historyQueryExcludesActiveAndUsesArchivedSnapshotsAndLatestAttempts() {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        assertTrue(history.listArchived(bank.assetId()).isEmpty());
        runtime.select("opt_b"); runtime.submit(); runtime.retry(); runtime.select("opt_a"); runtime.submit();
        runtime.goTo(1); runtime.select("opt_d");
        String archivedId = runtime.sessionId();
        runtime.restart();
        var entries = history.listArchived(bank.assetId());
        assertEquals(1, entries.size());
        assertEquals(archivedId, entries.getFirst().sessionId());
        assertEquals(new PracticeSummary(2, 1, 1, 0, 1, java.util.OptionalInt.of(100)), entries.getFirst().summary());
        assertEquals(PracticeSession.Status.ACTIVE, session().status());
        runtime.select("opt_b"); runtime.submit(); // An ACTIVE round is never History.
        assertEquals(entries, history.listArchived(bank.assetId()));
        var original = bank.questions().getFirst();
        var changed = Question.choice(original.id(), original.type(), new TextContent("changed"), original.analysis(), original.sourceRefs(), original.choicePayload(), original.choiceAnswerSpec());
        var newer = new QuestionBank(bank.assetId(), bank.title(), "2.0", List.of(), List.of(changed), List.of());
        service.openOrCreateActiveSession(newer, codec.contentId(newer));
        assertEquals(entries, history.listArchived(bank.assetId()));
    }

    @Test void historyOrderingAndRetryingUnfinishedWithZeroAccuracy() {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        runtime.select("opt_b"); runtime.submit(); runtime.retry();
        String first = runtime.sessionId(); runtime.restart();
        assertEquals(new PracticeSummary(2, 0, 0, 0, 2, java.util.OptionalInt.empty()),
                history.listArchived(bank.assetId()).getFirst().summary());
        var laterService = new PracticeSessionService(new SqlitePracticeTransaction(database),
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        var later = new PersistentPracticeRuntime(laterService, bank, codec.contentId(bank));
        later.select("opt_b"); later.submit();
        String second = later.sessionId(); later.restart();
        assertEquals(List.of(second, first), history.listArchived(bank.assetId()).stream()
                .map(PracticeHistoryEntry::sessionId).toList());
        assertEquals(new PracticeSummary(2, 1, 0, 1, 1, java.util.OptionalInt.of(0)),
                history.listArchived(bank.assetId()).getFirst().summary());
    }

    @Test void deletingArchivedCascadesOnlyThatRoundAndRejectsActiveOrOtherBank() {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        runtime.select("opt_b"); runtime.submit();
        String oldId = runtime.sessionId();
        String oldQuestionId = question("q_one").id();
        runtime.restart();
        String activeId = runtime.sessionId();
        assertThrows(IllegalStateException.class, () -> history.deleteArchivedSession(bank.assetId(), activeId));
        assertThrows(IllegalStateException.class, () -> history.deleteArchivedSession("qb_other", oldId));
        assertThrows(IllegalArgumentException.class, () -> history.deleteArchivedSession(bank.assetId(), "missing"));
        history.deleteArchivedSession(bank.assetId(), oldId);
        assertTrue(new SqlitePracticeSessionRepository(database).findById(oldId).isEmpty());
        assertTrue(new SqlitePracticeSessionQuestionRepository(database).findBySessionId(oldId).isEmpty());
        assertTrue(new SqliteQuestionAttemptRepository(database).listBySessionQuestion(oldQuestionId).isEmpty());
        assertEquals(activeId, session().id());
        assertTrue(history.listArchived(bank.assetId()).isEmpty());
    }

    @Test void failedHistoryDeleteRollsBackAndLeavesCardSourceRows() throws Exception {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        runtime.select("opt_b"); runtime.submit(); String archivedId = runtime.sessionId(); runtime.restart();
        var before = history.listArchived(bank.assetId());
        sql("CREATE TRIGGER fail_history_delete BEFORE DELETE ON practice_session BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        assertThrows(RuntimeException.class, () -> history.deleteArchivedSession(bank.assetId(), archivedId));
        assertEquals(before, history.listArchived(bank.assetId()));
        sql("DROP TRIGGER fail_history_delete");
    }

    @Test void deletingOneArchivedRoundPreservesOtherArchivedRound() {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        String first = runtime.sessionId(); runtime.restart();
        String second = runtime.sessionId(); runtime.restart();
        String active = runtime.sessionId();
        history.deleteArchivedSession(bank.assetId(), first);
        assertEquals(List.of(second), history.listArchived(bank.assetId()).stream()
                .map(PracticeHistoryEntry::sessionId).toList());
        assertEquals(PracticeSession.Status.ACTIVE,
                new SqlitePracticeSessionRepository(database).findById(active).orElseThrow().status());
    }

    @Test void archivedDetailLoadsOrderedSnapshotAndAllAttemptsWithoutCurrentBank() {
        runtime.select("opt_b"); runtime.submit(); runtime.retry(); runtime.select("opt_a"); runtime.submit();
        runtime.goTo(1); runtime.select("opt_d");
        String archived = runtime.sessionId();
        runtime.restart();
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        var detail = history.loadArchivedSessionDetail(bank.assetId(), archived);
        assertEquals(archived, detail.sessionId());
        assertEquals("Runtime", detail.bankTitle());
        assertEquals(List.of("q_one", "q_two"), detail.questions().stream().map(PracticeHistoryDetail.Question::questionId).toList());
        var first = detail.questions().getFirst();
        assertEquals(0, first.questionOrder());
        assertEquals("Single?", first.stem());
        assertEquals(List.of(new PracticeHistoryDetail.Option("opt_a", "Yes"),
                new PracticeHistoryDetail.Option("opt_b", "No")), first.options());
        assertEquals(List.of("opt_a"), first.correctOptionIds());
        assertEquals("Single analysis", first.analysis());
        assertEquals("Choices", ((java.util.Map<?, ?>) ((List<?>) first.sourceRefs().value()).getFirst()).get("anchorName"));
        assertEquals(List.of(1, 2), first.attempts().stream().map(PracticeHistoryDetail.Attempt::attemptNo).toList());
        assertEquals(List.of(QuestionAttempt.Mode.INITIAL, QuestionAttempt.Mode.RETRY),
                first.attempts().stream().map(PracticeHistoryDetail.Attempt::mode).toList());
        assertEquals(List.of(QuestionAttempt.Result.INCORRECT, QuestionAttempt.Result.CORRECT),
                first.attempts().stream().map(PracticeHistoryDetail.Attempt::result).toList());
        assertEquals(new PracticePayload(List.of("opt_b")), first.attempts().getFirst().answer());
        assertEquals(new PracticePayload(List.of("opt_a")), first.attempts().getLast().answer());
        assertEquals(PracticeSessionQuestion.State.DRAFT, detail.questions().get(1).finalState());
        assertEquals(new PracticePayload(List.of("opt_d")), detail.questions().get(1).draftAnswer());
        assertTrue(detail.questions().get(1).attempts().isEmpty());

        var changed = Question.choice("q_one", "SINGLE_CHOICE", new TextContent("Changed stem"), new TextContent("Changed analysis"), bank.questions().getFirst().sourceRefs(), new ChoicePayload(List.of(new ChoiceOption("opt_a", new TextContent("Changed option")),
                        new ChoiceOption("opt_b", new TextContent("No")))), new ChoiceAnswerSpec(List.of("opt_b")));
        var current = new QuestionBank(bank.assetId(), bank.title(), "2.0", List.of(), List.of(changed), List.of());
        service.openOrCreateActiveSession(current, codec.contentId(current));
        assertEquals(detail, history.loadArchivedSessionDetail(bank.assetId(), archived));
    }

    @Test void archivedDetailRejectsActiveWrongBankAndMissingSession() {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        String active = runtime.sessionId();
        assertThrows(IllegalStateException.class, () -> history.loadArchivedSessionDetail(bank.assetId(), active));
        runtime.restart();
        assertThrows(IllegalStateException.class, () -> history.loadArchivedSessionDetail("qb_other", active));
        assertThrows(IllegalArgumentException.class, () -> history.loadArchivedSessionDetail(bank.assetId(), "missing"));
    }

    @Test void archivedDetailKeepsUnansweredAndRetryingSeparateFromEarlierAttempts() {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        String unanswered = runtime.sessionId(); runtime.restart();
        assertEquals(PracticeSessionQuestion.State.UNANSWERED,
                history.loadArchivedSessionDetail(bank.assetId(), unanswered).questions().getFirst().finalState());
        assertTrue(history.loadArchivedSessionDetail(bank.assetId(), unanswered).questions().getFirst().attempts().isEmpty());

        runtime.select("opt_b"); runtime.submit(); runtime.retry(); runtime.select("opt_a");
        String retrying = runtime.sessionId(); runtime.restart();
        var question = history.loadArchivedSessionDetail(bank.assetId(), retrying).questions().getFirst();
        assertEquals(PracticeSessionQuestion.State.RETRYING, question.finalState());
        assertEquals(new PracticePayload(List.of("opt_a")), question.draftAnswer());
        assertEquals(1, question.attempts().size());
        assertEquals(QuestionAttempt.Result.INCORRECT, question.attempts().getFirst().result());
    }

    @Test void archivedDetailRetainsRevisionAttemptPayloadAndDoesNotWriteOnRead() {
        runtime.select("opt_b"); runtime.submit();
        String archived = runtime.sessionId();
        String rowId = question("q_one").id();
        new SqliteQuestionAttemptRepository(database).append(new QuestionAttempt("attempt_revision", rowId, 2,
                QuestionAttempt.Mode.REVISION, new PracticePayload(List.of("opt_a")),
                QuestionAttempt.Result.CORRECT, 1.0, 1.0, NOW.plusSeconds(20)));
        runtime.restart();
        var sessions = new SqlitePracticeSessionRepository(database);
        var before = sessions.findById(archived).orElseThrow();
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        var detail = history.loadArchivedSessionDetail(bank.assetId(), archived);
        assertEquals(QuestionAttempt.Mode.REVISION, detail.questions().getFirst().attempts().getLast().mode());
        assertEquals(new PracticePayload(List.of("opt_a")), detail.questions().getFirst().attempts().getLast().answer());
        assertEquals(QuestionAttempt.Result.CORRECT, detail.questions().getFirst().attempts().getLast().result());
        assertEquals(1.0, detail.questions().getFirst().attempts().getLast().score());
        assertEquals(1.0, detail.questions().getFirst().attempts().getLast().maxScore());
        assertEquals(before, sessions.findById(archived).orElseThrow());
        assertEquals(detail, history.loadArchivedSessionDetail(bank.assetId(), archived));
    }

    @Test void editSaveReopenSynchronizesNonSemanticThenSemanticRevisionAndRejectsStaleRuntime() throws Exception {
        Path file = temp.resolve("bank.qbank"); QBankTestPackageBuilder.write(file, codec.write(bank));
        runtime.select("opt_a"); runtime.submit();
        String id = runtime.sessionId();
        var old = bank.questions().getFirst();
        var changed = Question.choice(old.id(), old.type(), old.prompt(), new TextContent("new analysis"), old.sourceRefs(), old.choicePayload(), old.choiceAnswerSpec());
        var edited = new QuestionBank(bank.assetId(), bank.title(), "2.0", List.of(), List.of(changed, bank.questions().get(1)), List.of());
        QBankTestPackageBuilder.write(file, codec.write(edited));
        var reread = codec.parse(QBankTestPackageBuilder.read(file));
        var reopened = new PersistentPracticeRuntime(service(database), reread, codec.contentId(reread));
        assertEquals(id, reopened.sessionId()); assertTrue(reopened.session().correct());
        assertEquals("new analysis", QuestionText.analysis(reopened.session().current()));
        assertThrows(IllegalStateException.class, runtime::next);
        changed = Question.choice(old.id(), old.type(), new TextContent("new stem"), changed.analysis(), old.sourceRefs(), old.choicePayload(), old.choiceAnswerSpec());
        edited = new QuestionBank(bank.assetId(), bank.title(), "2.0", List.of(), List.of(changed, bank.questions().get(1)), List.of());
        QBankTestPackageBuilder.write(file, codec.write(edited));
        reread = codec.parse(QBankTestPackageBuilder.read(file));
        reopened = new PersistentPracticeRuntime(service(database), reread, codec.contentId(reread));
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, reopened.session().state());
        assertTrue(attempts("q_one").isEmpty());
    }

    @Test void archivedSessionCannotReceiveCommandsAndOpenCreatesAnotherActiveWithoutTouchingHistory() {
        runtime.select("opt_b"); runtime.submit();
        var saved = question("q_one"); var savedAttempts = attempts("q_one");
        var sessions = new SqlitePracticeSessionRepository(database);
        sessions.archive(runtime.sessionId(), NOW);
        var archived = session();
        assertThrows(IllegalStateException.class, runtime::next);
        assertThrows(IllegalStateException.class, () -> service.saveDraft(runtime.sessionId(), codec.contentId(bank), "q_one", Set.of("opt_a")));
        assertThrows(IllegalStateException.class, runtime::submit);
        var reopened = new PersistentPracticeRuntime(service(database), bank, codec.contentId(bank));
        assertNotEquals(runtime.sessionId(), reopened.sessionId());
        assertEquals(saved, question("q_one")); assertEquals(savedAttempts, attempts("q_one")); assertEquals(archived, session());
    }

    @Test void workspaceProviderIsolatesSameBankIdentityAndRebuildsWithoutDeletingRegistry() throws Exception {
        var paths = new WorkspacePathResolver(new QuizForgeDataDirectory(temp.resolve("workspace-data")));
        var one = new Workspace(WorkspaceId.newId(), "one", NOW, NOW);
        var two = new Workspace(WorkspaceId.newId(), "two", NOW, NOW);
        paths.create(one); paths.create(two);
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(paths.workspaceRoot(one.id()).resolve("question-banks/review.qbank"),bank);
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(paths.workspaceRoot(two.id()).resolve("question-banks/review.qbank"),bank);
        Path registry = paths.workspaceRoot(one.id()).resolve(".quizforge/workspace.db");
        byte[] registryBefore = Files.readAllBytes(registry);
        var provider = new SqliteWorkspacePracticeRuntimeProvider(paths, codec, Clock.fixed(NOW, ZoneOffset.UTC));
        var a = provider.open(one.id(), bank); a.select("opt_b");
        var b = provider.open(two.id(), bank);
        assertNotEquals(a.sessionId(), b.sessionId());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, b.session().state());
        provider = new SqliteWorkspacePracticeRuntimeProvider(paths, codec, Clock.fixed(NOW, ZoneOffset.UTC));
        var restored = provider.open(one.id(), codec.parse(codec.write(bank)));
        assertEquals(a.sessionId(), restored.sessionId()); assertEquals(Set.of("opt_b"), restored.session().selected());
        assertArrayEquals(registryBefore, Files.readAllBytes(registry));
        assertTrue(Files.exists(paths.workspaceRoot(one.id()).resolve(".quizforge/quizforge.db")));
        assertFalse(Files.exists(paths.workspaceRoot(one.id()).resolve(".quizforge/workspaces")));
    }

    @Test void mixedBankKeepsChoiceAttemptsAndAddsEssaySnapshots() throws Exception {
        var paths = new WorkspacePathResolver(new QuizForgeDataDirectory(temp.resolve("mixed-workspace")));
        var workspace = new Workspace(WorkspaceId.newId(), "Mixed", NOW, NOW);
        paths.create(workspace);
        var provider = new SqliteWorkspacePracticeRuntimeProvider(paths, codec, Clock.fixed(NOW, ZoneOffset.UTC));
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(paths.workspaceRoot(workspace.id()).resolve("question-banks/review.qbank"),bank);
        var original = provider.open(workspace.id(), bank);
        original.select("opt_a"); original.submit();
        var essay = io.quizforge.infrastructure.testing.EssayTestBanks.bank().questions().getFirst();
        var mixed = new QuestionBank(bank.assetId(), bank.title(), List.of(),
                List.of(essay, bank.questions().get(0), bank.questions().get(1)), List.of());
        var db = new SqliteDatabase(paths.workspaceRoot(workspace.id()).resolve(".quizforge/quizforge.db"));
        var questions = new SqlitePracticeSessionQuestionRepository(db);
        var attempts = new SqliteQuestionAttemptRepository(db);
        var priorRows = questions.findBySessionId(original.sessionId());
        var priorAttempts = attempts.listBySessionQuestion(priorRows.getFirst().id());
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(paths.workspaceRoot(workspace.id()).resolve("question-banks/review.qbank"),mixed);
        var restored = provider.open(workspace.id(), mixed);
        assertEquals(original.sessionId(), restored.sessionId());
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, restored.session().state());
        assertEquals(3,questions.findBySessionId(restored.sessionId()).size());
        assertEquals(priorRows.getFirst().id(),questions.findBySessionIdAndQuestionId(restored.sessionId(),"q_one").orElseThrow().id());
        assertEquals(priorAttempts, attempts.listBySessionQuestion(priorRows.getFirst().id()));
        assertEquals(codec.contentId(mixed), new SqlitePracticeSessionRepository(db)
                .findById(restored.sessionId()).orElseThrow().questionBankContentId());
        assertEquals(List.of("q_essay","q_one", "q_two"), restored.session().bank().questions().stream().map(Question::id).toList());
        restored.goTo(2); restored.select("opt_d"); restored.select("opt_e");
        var editedEssay = io.quizforge.infrastructure.testing.EssayTestBanks.essay(essay.id(),
                new TextContent("Edited essay only"), essay.essayPayload(), null);
        var edited = new QuestionBank(bank.assetId(), bank.title(), List.of(),
                List.of(editedEssay, bank.questions().get(0), bank.questions().get(1)), List.of());
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(paths.workspaceRoot(workspace.id()).resolve("question-banks/review.qbank"),edited);
        var afterEdit = provider.open(workspace.id(), edited);
        assertEquals(Set.of("opt_d", "opt_e"), afterEdit.session().selected());
        assertEquals(priorAttempts, attempts.listBySessionQuestion(priorRows.getFirst().id()));
        afterEdit.submit(); afterEdit.next();
        assertEquals(3, afterEdit.summary().totalCount()); assertEquals(2, afterEdit.summary().correctCount());
        String archivedId = afterEdit.sessionId(); afterEdit.restart();
        var history = provider.history(workspace.id()).loadArchivedSessionDetail(bank.assetId(), archivedId);
        assertEquals(List.of("ESSAY","SINGLE_CHOICE", "MULTIPLE_CHOICE"), history.questions().stream()
                .map(PracticeHistoryDetail.Question::questionType).toList());
        assertEquals(priorAttempts, attempts.listBySessionQuestion(priorRows.getFirst().id()));
        var pureEssay=io.quizforge.infrastructure.testing.EssayTestBanks.bank();
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(paths.workspaceRoot(workspace.id()).resolve("question-banks/essay.qbank"),pureEssay);
        assertEquals(2,provider.open(workspace.id(),pureEssay).session().bank().questions().size());
    }

    @Test void duplicatePortableIdentityCannotChangeExistingAttemptsAndRenameStillRestores() throws Exception {
        var paths=new WorkspacePathResolver(new QuizForgeDataDirectory(temp.resolve("identity-data")));
        var workspace=new Workspace(WorkspaceId.newId(),"identity",NOW,NOW);paths.create(workspace);
        Path root=paths.workspaceRoot(workspace.id()), file=root.resolve("question-banks/original.qbank");
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(file,bank);
        var provider=new SqliteWorkspacePracticeRuntimeProvider(paths,codec,Clock.fixed(NOW,ZoneOffset.UTC));
        var original=provider.open(workspace.id(),bank);original.select("opt_a");original.submit();
        var attempts=new SqliteQuestionAttemptRepository(new SqliteDatabase(root.resolve(".quizforge/quizforge.db")));
        var questions=new SqlitePracticeSessionQuestionRepository(new SqliteDatabase(root.resolve(".quizforge/quizforge.db")));
        var row=questions.findBySessionIdAndQuestionId(original.sessionId(),"q_one").orElseThrow();
        var facts=attempts.listBySessionQuestion(row.id());
        Path copy=root.resolve("question-banks/copy.qbank");Files.copy(file,copy);
        assertThrows(IllegalStateException.class,()->provider.open(workspace.id(),bank));
        assertEquals(facts,attempts.listBySessionQuestion(row.id()));
        Files.delete(copy);Files.move(file,file.resolveSibling("renamed.qbank"));
        assertEquals(original.sessionId(),provider.open(workspace.id(),bank).sessionId());
        assertEquals(facts,attempts.listBySessionQuestion(row.id()));
    }

    @Test void accuracyCountsSubmittedQuestionsIncludingUnscoredEssayButExcludesDrafts() {
        runtime.select("opt_a");runtime.submit();
        assertEquals(100,runtime.summary().accuracyPercent().orElseThrow());
        runtime.next();runtime.select("opt_d");
        assertEquals(100,runtime.summary().accuracyPercent().orElseThrow());
        var essay=io.quizforge.infrastructure.testing.EssayTestBanks.bank().questions().getFirst();
        var mixed=new QuestionBank("qb_accuracy","accuracy",List.of(),List.of(bank.questions().getFirst(),essay),List.of());
        var active=new PersistentPracticeRuntime(service,mixed,codec.contentId(mixed));
        active.select("opt_a");active.submit();active.next();active.saveEssayDraft(essay.id(),new EssayPracticeAnswer("answer",null));active.submit();
        assertEquals(2,active.summary().submittedCount());assertEquals(1,active.summary().unscoredCount());
        assertEquals(50,active.summary().accuracyPercent().orElseThrow());
    }

    @Test void unsubmittedEssayDocumentAndEmbeddedImageSurviveDatabaseReopenAndClear() throws Exception {
        var essayBank=io.quizforge.infrastructure.testing.EssayTestBanks.bank();
        var image=java.util.Base64.getEncoder().encodeToString(io.quizforge.infrastructure.testing.EssayTestBanks.image("png"));
        String document="{\"version\":\"1.0.4\",\"options\":{},\"data\":{\"main\":[{\"value\":\"未提交的作文\\n第二行\"},{\"type\":\"image\",\"value\":\"data:image/png;base64,"+image+"\",\"width\":24,\"height\":16}]}}";
        var answer=new EssayPracticeAnswer("未提交的作文\n第二行",document);
        var essay=new PersistentPracticeRuntime(service,essayBank,codec.contentId(essayBank));
        essay.saveEssayDraft("q_essay",answer);
        var questions=new SqlitePracticeSessionQuestionRepository(database);
        var row=questions.findBySessionIdAndQuestionId(essay.sessionId(),"q_essay").orElseThrow();
        assertEquals(PracticeSessionQuestion.State.DRAFT,row.practiceState());
        assertTrue(new SqliteQuestionAttemptRepository(database).listBySessionQuestion(row.id()).isEmpty());
        essay.goTo(1);
        var reopenedDb=new SqliteDatabase(new QuizForgeDataDirectory(temp));
        var restored=new PersistentPracticeRuntime(service(reopenedDb),essayBank,codec.contentId(essayBank));
        assertEquals(essay.sessionId(),restored.sessionId());assertEquals(1,restored.session().index());
        assertEquals(answer,restored.essayAnswer("q_essay"));
        restored.goTo(0);restored.saveEssayDraft("q_essay",new EssayPracticeAnswer("",null));
        var cleared=new PersistentPracticeRuntime(service(reopenedDb),essayBank,codec.contentId(essayBank));
        assertTrue(cleared.essayAnswer("q_essay").empty());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED,cleared.session().state());
        assertNull(questions.findBySessionIdAndQuestionId(cleared.sessionId(),"q_essay").orElseThrow().draftAnswer());
    }

    @Test void essaySubmissionLocksAnswerUntilRetryAndArchivesRetryDraft() {
        var essayBank=io.quizforge.infrastructure.testing.EssayTestBanks.bank();
        var essay=new PersistentPracticeRuntime(service,essayBank,codec.contentId(essayBank));
        essay.saveEssayDraft("q_essay",new EssayPracticeAnswer("Initial answer",null));essay.submit();
        assertEquals(1,essay.summary().submittedCount());assertEquals(1,essay.summary().unscoredCount());
        assertEquals(0,essay.summary().incorrectCount());
        var row=essay.questionState("q_essay");var initial=row.attempts().getFirst();
        assertEquals(QuestionAttempt.Result.UNSCORED,initial.result());assertNull(row.sessionQuestion().draftAnswer());
        assertThrows(IllegalStateException.class,()->essay.saveEssayDraft("q_essay",new EssayPracticeAnswer("Direct edit",null)));
        essay.retry();
        essay.saveEssayDraft("q_essay",new EssayPracticeAnswer("Revising without submitting",null));
        var restored=new PersistentPracticeRuntime(service(database),essayBank,codec.contentId(essayBank));
        assertEquals("Revising without submitting",restored.essayAnswer("q_essay").text());
        assertEquals(PracticeSessionQuestion.State.RETRYING,restored.questionState("q_essay").sessionQuestion().practiceState());
        assertEquals(List.of(initial),restored.questionState("q_essay").attempts());
        String archived=restored.sessionId();restored.restart();
        var history=new PracticeHistoryService(new SqlitePracticeTransaction(database)).loadArchivedSessionDetail(essayBank.assetId(),archived);
        assertEquals("Revising without submitting",EssayPracticeAnswer.from(history.questions().getFirst().draftAnswer()).text());
        assertEquals(1,history.questions().getFirst().attempts().size());
    }

    @Test void previousChoiceOnlyActiveAtSameRevisionExpandsWithoutLosingChoiceDraft() {
        var essay=io.quizforge.infrastructure.testing.EssayTestBanks.bank().questions().getFirst();
        var mixed=new QuestionBank(bank.assetId(),bank.title(),List.of(),List.of(essay,bank.questions().get(0),bank.questions().get(1)),List.of());
        // Model the old provider: persisted choices, but the full mixed-bank contentId.
        var old=new PersistentPracticeRuntime(service,bank,codec.contentId(mixed));old.select("opt_b");
        var restored=new PersistentPracticeRuntime(service,mixed,codec.contentId(mixed));
        assertEquals(old.sessionId(),restored.sessionId());assertEquals(1,restored.session().index());
        assertEquals(Set.of("opt_b"),restored.session().selected());
        assertEquals(PracticeSessionQuestion.State.UNANSWERED,restored.questionState(essay.id()).sessionQuestion().practiceState());
        assertTrue(restored.questionState("q_one").attempts().isEmpty());
    }

    @Test void essayHistoryOwnsPromptResourcesScoreAndReferenceWithoutResettingExistingAttempt() throws Exception {
        byte[] bytes=("{\"version\":\"1.0.4\",\"options\":{},\"data\":{\"main\":[{\"value\":\"Directions\",\"bold\":true},{\"type\":\"image\",\"value\":\"data:image/png;base64,"
                +java.util.Base64.getEncoder().encodeToString(io.quizforge.infrastructure.testing.EssayTestBanks.image("png"))+"\",\"width\":24,\"height\":16}]}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var resource=new QBankResource("res_canvas_"+hash,ResourceKind.DOCUMENT,"application/vnd.quizforge.canvas+json","resources/prompt.canvas.json",hash);
        var essay=new Question("q_history_essay","ESSAY",List.of(),new DocumentContent(resource.id(),"Directions"),new EssayPayload(null),
                new EssayAnswerSpec(new TextContent("Reference answer")),new ScoreSpec(new java.math.BigDecimal("20.25")),
                new EvaluationSpec(List.of(new EvaluationCriterion("content","Content",java.math.BigDecimal.ONE)),"Rubric guidance"),new TextContent("Analysis"),List.of());
        var original=new QuestionBank("qb_history_essay","History",List.of(),List.of(essay),List.of(resource));
        io.quizforge.core.port.QuestionResourceInput input=r->new java.io.ByteArrayInputStream(bytes);
        String revision=codec.contentId(original);
        var active=new PersistentPracticeRuntime(service,original,revision,input);
        active.saveEssayDraft(essay.id(),new EssayPracticeAnswer("My answer",null));active.submit();
        var before=active.questionState(essay.id());
        // Simulate an existing ACTIVE snapshot from before presentation resources were saved.
        new SqlitePracticeSessionQuestionRepository(database).updateSnapshot(active.sessionId(),essay.id(),0,
                PracticeQuestionSnapshotMapper.logical(before.sessionQuestion().snapshot()),NOW);
        var enriched=new PersistentPracticeRuntime(service,original,revision,input);
        assertEquals(before.attempts(),enriched.questionState(essay.id()).attempts());
        assertEquals(before.sessionQuestion().id(),enriched.questionState(essay.id()).sessionQuestion().id());
        String archived=enriched.sessionId();enriched.restart();
        var history=new PracticeHistoryService(new SqlitePracticeTransaction(new SqliteDatabase(new QuizForgeDataDirectory(temp))))
                .loadArchivedSessionDetail(original.assetId(),archived);
        var fields=QuestionContentData.map(history.questions().getFirst().contentSnapshot().value());
        var saved=EssayQuestionSnapshot.from(new PracticePayload(fields.get("essayPresentation")));
        assertEquals(revision,history.bankContentId());assertEquals(essay.prompt(),saved.prompt());
        assertEquals(new java.math.BigDecimal("20.25"),saved.maxScore());
        assertEquals(new TextContent("Reference answer"),saved.reference());assertEquals(new TextContent("Analysis"),saved.analysis());
        assertEquals("Rubric guidance",saved.guidance());
        try(var stream=saved.open(resource)){assertArrayEquals(bytes,stream.readAllBytes());}
        assertEquals(1,history.questions().getFirst().attempts().size());
        assertEquals("My answer",EssayPracticeAnswer.from(history.questions().getFirst().attempts().getFirst().answer()).text());
    }

    private PracticeSession session() { return new SqlitePracticeSessionRepository(database).findById(runtime.sessionId()).orElseThrow(); }
    private PracticeSessionQuestion question(String id) { return new SqlitePracticeSessionQuestionRepository(database).findBySessionIdAndQuestionId(runtime.sessionId(), id).orElseThrow(); }
    private List<QuestionAttempt> attempts(String id) { return new SqliteQuestionAttemptRepository(database).listBySessionQuestion(question(id).id()); }
    private PracticeSessionService service(SqliteDatabase db) { return new PracticeSessionService(new SqlitePracticeTransaction(db), Clock.fixed(NOW, ZoneOffset.UTC)); }
    private void sql(String text) throws Exception { try (var connection = database.openConnection(); var statement = connection.createStatement()) { statement.execute(text); } }
    private QuestionBank bank() {
        String revision = "qfd:v2:" + "b".repeat(64);
        var source = new QuestionSourceDocument("doc_runtime", revision, "Runtime source");
        var ref = SourceRef.anchor("doc_runtime", revision, "Choices", 1, "Runtime source", "Choices");
        var single = Question.choice("q_one", "SINGLE_CHOICE", new TextContent("Single?"), new TextContent("Single analysis"), List.of(ref), new ChoicePayload(List.of(new ChoiceOption("opt_a", new TextContent("Yes")), new ChoiceOption("opt_b", new TextContent("No")))), new ChoiceAnswerSpec(List.of("opt_a")));
        var multiple = Question.choice("q_two", "MULTIPLE_CHOICE", new TextContent("Multiple?"), new TextContent("Multiple analysis"), List.of(ref), new ChoicePayload(List.of(new ChoiceOption("opt_d", new TextContent("One")), new ChoiceOption("opt_e", new TextContent("Two")), new ChoiceOption("opt_f", new TextContent("Wrong")))), new ChoiceAnswerSpec(List.of("opt_d", "opt_e")));
        return new QuestionBank("qb_runtime", "Runtime", "2.0", List.of(), List.of(single, multiple), List.of());
    }
}
