package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.practice.*;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankPracticeSession;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.infrastructure.filesystem.*;
import io.quizforge.infrastructure.persistence.*;
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

class PersistentPracticeRuntimeIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-28T05:00:00Z");
    @TempDir Path temp;
    private SqliteDatabase database;
    private PracticeSessionService service;
    private PersistentPracticeRuntime runtime;
    private QuestionBankFile bank;
    private final QuestionBankV1Codec codec = new QuestionBankV1Codec();

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
        assertEquals(1, new SqlitePracticeSessionRepository(database).listArchivedByQuestionBankAssetId(bank.id()).size());
    }

    @Test void restartUsesUpdatedBankSnapshotWithoutChangingArchivedQuestion() {
        runtime.select("opt_a"); runtime.submit();
        String oldId = runtime.sessionId();
        var oldRow = question("q_one");
        var first = bank.questions().getFirst();
        var changed = new QuestionBankFile.Entry(first.id(), first.type(), first.stem() + " updated", first.analysis(), first.sourceRefs(), first.data());
        var newer = new QuestionBankFile(bank.format(), bank.schemaVersion(), bank.id(), bank.title(),
                bank.sourceDocuments(), List.of(changed, bank.questions().get(1)));
        var snapshot = service.restartPractice(oldId, codec.contentId(bank), newer, codec.contentId(newer));
        assertEquals(codec.contentId(newer), snapshot.session().questionBankContentId());
        assertEquals(changed.stem(), snapshot.questions().getFirst().sessionQuestion().snapshot().stem());
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
        assertTrue(new SqlitePracticeSessionRepository(database).listArchivedByQuestionBankAssetId(bank.id()).isEmpty());
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
        assertTrue(history.listArchived(bank.id()).isEmpty());
        runtime.select("opt_b"); runtime.submit(); runtime.retry(); runtime.select("opt_a"); runtime.submit();
        runtime.goTo(1); runtime.select("opt_d");
        String archivedId = runtime.sessionId();
        runtime.restart();
        var entries = history.listArchived(bank.id());
        assertEquals(1, entries.size());
        assertEquals(archivedId, entries.getFirst().sessionId());
        assertEquals(new PracticeSummary(2, 1, 1, 0, 1, java.util.OptionalInt.of(100)), entries.getFirst().summary());
        assertEquals(PracticeSession.Status.ACTIVE, session().status());
        runtime.select("opt_b"); runtime.submit(); // An ACTIVE round is never History.
        assertEquals(entries, history.listArchived(bank.id()));
        var original = bank.questions().getFirst();
        var changed = new QuestionBankFile.Entry(original.id(), original.type(), "changed", original.analysis(),
                original.sourceRefs(), original.data());
        var newer = new QuestionBankFile(bank.format(), bank.schemaVersion(), bank.id(), bank.title(),
                bank.sourceDocuments(), List.of(changed));
        service.openOrCreateActiveSession(newer, codec.contentId(newer));
        assertEquals(entries, history.listArchived(bank.id()));
    }

    @Test void historyOrderingAndRetryingUnfinishedWithZeroAccuracy() {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        runtime.select("opt_b"); runtime.submit(); runtime.retry();
        String first = runtime.sessionId(); runtime.restart();
        assertEquals(new PracticeSummary(2, 0, 0, 0, 2, java.util.OptionalInt.empty()),
                history.listArchived(bank.id()).getFirst().summary());
        var laterService = new PracticeSessionService(new SqlitePracticeTransaction(database),
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        var later = new PersistentPracticeRuntime(laterService, bank, codec.contentId(bank));
        later.select("opt_b"); later.submit();
        String second = later.sessionId(); later.restart();
        assertEquals(List.of(second, first), history.listArchived(bank.id()).stream()
                .map(PracticeHistoryEntry::sessionId).toList());
        assertEquals(new PracticeSummary(2, 1, 0, 1, 1, java.util.OptionalInt.of(0)),
                history.listArchived(bank.id()).getFirst().summary());
    }

    @Test void deletingArchivedCascadesOnlyThatRoundAndRejectsActiveOrOtherBank() {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        runtime.select("opt_b"); runtime.submit();
        String oldId = runtime.sessionId();
        String oldQuestionId = question("q_one").id();
        runtime.restart();
        String activeId = runtime.sessionId();
        assertThrows(IllegalStateException.class, () -> history.deleteArchivedSession(bank.id(), activeId));
        assertThrows(IllegalStateException.class, () -> history.deleteArchivedSession("qb_other", oldId));
        assertThrows(IllegalArgumentException.class, () -> history.deleteArchivedSession(bank.id(), "missing"));
        history.deleteArchivedSession(bank.id(), oldId);
        assertTrue(new SqlitePracticeSessionRepository(database).findById(oldId).isEmpty());
        assertTrue(new SqlitePracticeSessionQuestionRepository(database).findBySessionId(oldId).isEmpty());
        assertTrue(new SqliteQuestionAttemptRepository(database).listBySessionQuestion(oldQuestionId).isEmpty());
        assertEquals(activeId, session().id());
        assertTrue(history.listArchived(bank.id()).isEmpty());
    }

    @Test void failedHistoryDeleteRollsBackAndLeavesCardSourceRows() throws Exception {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        runtime.select("opt_b"); runtime.submit(); String archivedId = runtime.sessionId(); runtime.restart();
        var before = history.listArchived(bank.id());
        sql("CREATE TRIGGER fail_history_delete BEFORE DELETE ON practice_session BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        assertThrows(RuntimeException.class, () -> history.deleteArchivedSession(bank.id(), archivedId));
        assertEquals(before, history.listArchived(bank.id()));
        sql("DROP TRIGGER fail_history_delete");
    }

    @Test void deletingOneArchivedRoundPreservesOtherArchivedRound() {
        var history = new PracticeHistoryService(new SqlitePracticeTransaction(database));
        String first = runtime.sessionId(); runtime.restart();
        String second = runtime.sessionId(); runtime.restart();
        String active = runtime.sessionId();
        history.deleteArchivedSession(bank.id(), first);
        assertEquals(List.of(second), history.listArchived(bank.id()).stream()
                .map(PracticeHistoryEntry::sessionId).toList());
        assertEquals(PracticeSession.Status.ACTIVE,
                new SqlitePracticeSessionRepository(database).findById(active).orElseThrow().status());
    }

    @Test void editSaveReopenSynchronizesNonSemanticThenSemanticRevisionAndRejectsStaleRuntime() throws Exception {
        Path file = temp.resolve("bank.qbank"); Files.writeString(file, codec.write(bank));
        runtime.select("opt_a"); runtime.submit();
        String id = runtime.sessionId();
        var old = bank.questions().getFirst();
        var changed = new QuestionBankFile.Entry(old.id(), old.type(), old.stem(), "new analysis", old.sourceRefs(), old.data());
        var edited = new QuestionBankFile(bank.format(), bank.schemaVersion(), bank.id(), bank.title(), bank.sourceDocuments(), List.of(changed, bank.questions().get(1)));
        Files.writeString(file, codec.write(edited));
        var reread = codec.parse(Files.readString(file));
        var reopened = new PersistentPracticeRuntime(service(database), reread, codec.contentId(reread));
        assertEquals(id, reopened.sessionId()); assertTrue(reopened.session().correct());
        assertEquals("new analysis", reopened.session().current().analysis());
        assertThrows(IllegalStateException.class, runtime::next);
        changed = new QuestionBankFile.Entry(old.id(), old.type(), "new stem", changed.analysis(), old.sourceRefs(), old.data());
        edited = new QuestionBankFile(bank.format(), bank.schemaVersion(), bank.id(), bank.title(), bank.sourceDocuments(), List.of(changed, bank.questions().get(1)));
        Files.writeString(file, codec.write(edited));
        reread = codec.parse(Files.readString(file));
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

    private PracticeSession session() { return new SqlitePracticeSessionRepository(database).findById(runtime.sessionId()).orElseThrow(); }
    private PracticeSessionQuestion question(String id) { return new SqlitePracticeSessionQuestionRepository(database).findBySessionIdAndQuestionId(runtime.sessionId(), id).orElseThrow(); }
    private List<QuestionAttempt> attempts(String id) { return new SqliteQuestionAttemptRepository(database).listBySessionQuestion(question(id).id()); }
    private PracticeSessionService service(SqliteDatabase db) { return new PracticeSessionService(new SqlitePracticeTransaction(db), Clock.fixed(NOW, ZoneOffset.UTC)); }
    private void sql(String text) throws Exception { try (var connection = database.openConnection(); var statement = connection.createStatement()) { statement.execute(text); } }
    private QuestionBankFile bank() {
        String revision = "qfd:v2:" + "b".repeat(64);
        var source = new QuestionBankFile.SourceDocument("doc_runtime", revision, "Runtime source");
        var ref = QuestionBankFile.SourceRef.anchor("doc_runtime", revision, "Choices", 1, "Runtime source", "Choices");
        var single = new QuestionBankFile.Entry("q_one", "SINGLE_CHOICE", "Single?", "Single analysis", List.of(ref),
                new QuestionBankFile.Data(List.of(new QuestionBankFile.Option("opt_a", "Yes"), new QuestionBankFile.Option("opt_b", "No")), List.of("opt_a")));
        var multiple = new QuestionBankFile.Entry("q_two", "MULTIPLE_CHOICE", "Multiple?", "Multiple analysis", List.of(ref),
                new QuestionBankFile.Data(List.of(new QuestionBankFile.Option("opt_d", "One"), new QuestionBankFile.Option("opt_e", "Two"), new QuestionBankFile.Option("opt_f", "Wrong")), List.of("opt_d", "opt_e")));
        return new QuestionBankFile("quizforge-question-bank", "1.2", "qb_runtime", "Runtime", List.of(source), List.of(single, multiple));
    }
}
