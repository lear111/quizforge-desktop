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

    @Test void invalidAndIncompleteCommandsDoNotWriteAnything() {
        assertThrows(IllegalStateException.class, runtime::submit);
        assertThrows(IllegalArgumentException.class, () -> service.saveDraft(runtime.sessionId(), codec.contentId(bank), "q_one", Set.of("A")));
        assertThrows(IllegalArgumentException.class, () -> service.saveDraft(runtime.sessionId(), codec.contentId(bank), "q_one", Set.of("opt_a", "opt_b")));
        assertThrows(IllegalStateException.class, () -> service.updateCurrentView(runtime.sessionId(), codec.contentId(bank), PracticeSession.View.SUMMARY));
        assertThrows(IllegalArgumentException.class, () -> service.updateCurrentQuestion(runtime.sessionId(), codec.contentId(bank), "missing"));
        assertEquals(PracticeSessionQuestion.State.UNANSWERED, question("q_one").practiceState());
        assertEquals("q_one", session().currentQuestionId());
        assertTrue(attempts("q_one").isEmpty());
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
