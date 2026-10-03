package io.quizforge.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.PracticeSessionQuestionRepository;
import io.quizforge.core.port.PracticeSessionRepository;
import io.quizforge.core.port.QuestionAttemptRepository;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSession;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionQuestionRepository;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository;
import io.quizforge.infrastructure.persistence.practice.SqliteQuestionAttemptRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticePersistenceIntegrationTest {
    private static final Instant START = Instant.parse("2026-09-28T01:02:03.123456789Z");
    private static final String BANK_REVISION = "qfb:v2:" + "a".repeat(64);
    private static final String DOCUMENT_REVISION = "qfd:v2:" + "b".repeat(64);
    @TempDir Path temporaryDirectory;
    private QuizForgeDataDirectory directory;
    private SqliteDatabase database;
    private PracticeSessionRepository sessions;
    private PracticeSessionQuestionRepository questions;
    private QuestionAttemptRepository attempts;

    @BeforeEach void setUp() {
        directory = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        database = new SqliteDatabase(directory);
        sessions = new SqlitePracticeSessionRepository(database);
        questions = new SqlitePracticeSessionQuestionRepository(database);
        attempts = new SqliteQuestionAttemptRepository(database);
    }

    @Test void createsActiveSessionAndReopensWithoutLegacyBankOrWorkspaceRows() throws Exception {
        var session = session("session_1", "qb_1");
        sessions.create(session);
        var reopened = new SqlitePracticeSessionRepository(new SqliteDatabase(directory));
        assertEquals(Optional.of(session), reopened.findById(session.id()));
        assertEquals(Optional.of(session), reopened.findActiveByQuestionBankAssetId("qb_1"));
        assertEquals(0, rowCount("question_bank"));
        assertEquals(0, rowCount("workspace"));
        assertEquals(START, reopened.findById(session.id()).orElseThrow().startedAt());
    }

    @Test void databaseRejectsTwoActiveSessionsForSameAssetWithoutReplacingFirst() {
        var original = session("session_1", "qb_1");
        sessions.create(original);
        assertPersistenceFailure(() -> sessions.create(session("session_2", "qb_1")));
        assertEquals(Optional.of(original), sessions.findActiveByQuestionBankAssetId("qb_1"));
        assertTrue(sessions.findById("session_2").isEmpty());
    }

    @Test void differentQuestionBanksCanHaveActiveSessionsAtSameTime() {
        sessions.create(session("session_1", "qb_1"));
        sessions.create(session("session_2", "qb_2"));
        assertEquals("session_1", sessions.findActiveByQuestionBankAssetId("qb_1").orElseThrow().id());
        assertEquals("session_2", sessions.findActiveByQuestionBankAssetId("qb_2").orElseThrow().id());
    }

    @Test void archivingFreesActiveSlotAndPreservesOldRevisionAndPosition() {
        var original = session("session_1", "qb_1");
        sessions.create(original);
        Instant archivedAt = START.plusSeconds(30);
        sessions.archive(original.id(), archivedAt);
        var archived = sessions.findById(original.id()).orElseThrow();
        assertEquals(PracticeSession.Status.ARCHIVED, archived.status());
        assertEquals(archivedAt, archived.archivedAt());
        assertEquals(archivedAt, archived.lastActivityAt());
        assertEquals(original.questionBankContentId(), archived.questionBankContentId());
        assertEquals(original.currentQuestionId(), archived.currentQuestionId());
        sessions.create(session("session_2", "qb_1"));
        assertEquals("session_2", sessions.findActiveByQuestionBankAssetId("qb_1").orElseThrow().id());
        sessions.archive(original.id(), START.plusSeconds(90));
        sessions.touch(original.id(), START.plusSeconds(91));
        sessions.updateCurrentPosition(original.id(), PracticeSession.View.SUMMARY, null);
        assertEquals(archived, sessions.findById(original.id()).orElseThrow());
    }

    @Test void archivedListExcludesActiveAndOtherBanksAndOrdersNanosecondsDescending() {
        Instant wholeSecond = Instant.parse("2026-09-28T02:00:00Z");
        sessions.create(session("old", "qb_1"));
        sessions.archive("old", wholeSecond);
        sessions.create(session("newer", "qb_1"));
        sessions.archive("newer", wholeSecond.plusNanos(1));
        sessions.create(session("active", "qb_1"));
        sessions.create(session("other", "qb_other"));
        sessions.archive("other", wholeSecond.plusSeconds(100));
        var history = sessions.listArchivedByQuestionBankAssetId("qb_1");
        assertEquals(List.of("newer", "old"), history.stream().map(PracticeSession::id).toList());
        assertEquals(List.of(wholeSecond.plusNanos(1), wholeSecond),
                history.stream().map(PracticeSession::archivedAt).toList());
    }

    @Test void positionAndActivityRoundTripQuestionAndSummaryWithSqlNull() throws Exception {
        sessions.create(session("session_1", "qb_1"));
        sessions.updateCurrentPosition("session_1", PracticeSession.View.SUMMARY, null);
        var summary = sessions.findById("session_1").orElseThrow();
        assertEquals(PracticeSession.View.SUMMARY, summary.currentView());
        assertNull(summary.currentQuestionId());
        assertNull(summary.archivedAt());
        assertNull(cell("SELECT current_question_id FROM practice_session WHERE id = ?", "session_1"));
        Instant activity = START.plusNanos(10);
        sessions.updateCurrentPosition("session_1", PracticeSession.View.QUESTION, "q_second");
        sessions.touch("session_1", activity);
        var current = sessions.findById("session_1").orElseThrow();
        assertEquals(PracticeSession.View.QUESTION, current.currentView());
        assertEquals("q_second", current.currentQuestionId());
        assertEquals(activity, current.lastActivityAt());
        assertEquals(START, current.startedAt());
    }

    @Test void databaseRejectsQuestionViewWithoutQuestionIdAtomically() {
        var original = session("session_1", "qb_1");
        sessions.create(original);
        assertPersistenceFailure(() -> sessions.updateCurrentPosition("session_1", PracticeSession.View.QUESTION, null));
        assertEquals(Optional.of(original), sessions.findById("session_1"));
    }

    @Test void snapshotRoundTripRetainsAllJsonFieldsUnicodeAndOriginalSourceRevision() throws Exception {
        sessions.create(session("session_1", "qb_1"));
        var question = question("sq_1", "session_1", "q_original", 3);
        questions.create(question);
        var reopened = new SqlitePracticeSessionQuestionRepository(new SqliteDatabase(directory));
        assertEquals(List.of(question), reopened.findBySessionId("session_1"));
        assertEquals(Optional.of(question), reopened.findBySessionIdAndQuestionId("session_1", "q_original"));
        ObjectMapper json = new ObjectMapper();
        var options = json.readTree(cell("SELECT options_snapshot_json FROM practice_session_question WHERE id = ?", "sq_1"));
        assertTrue(options.isArray());
        assertEquals("带\"引号\"、\\和换行\n第二行", options.get(0).path("content").asText());
        var refs = json.readTree(cell("SELECT source_refs_snapshot_json FROM practice_session_question WHERE id = ?", "sq_1"));
        assertEquals(DOCUMENT_REVISION, refs.get(0).path("documentContentId").asText());
        assertEquals("定义", refs.get(0).path("anchorName").asText());
        assertEquals(2, refs.get(0).path("occurrence").asInt());
        assertNull(cell("SELECT draft_answer_json FROM practice_session_question WHERE id = ?", "sq_1"));
    }

    @Test void sessionQuestionsReturnGlobalOrderAcrossTypesAndRemainSessionScoped() {
        sessions.create(session("session_1", "qb_1"));
        sessions.create(session("session_2", "qb_2"));
        var first = question("sq_1", "session_1", "q_first", 0);
        var last = question("sq_3", "session_1", "q_last", 2);
        var second = new PracticeSessionQuestion("sq_2", "session_1", "q_second", 1,
                snapshot("MULTIPLE_CHOICE", "多选题"), PracticeSessionQuestion.State.UNANSWERED, null, START, START);
        questions.createAll(List.of(last, first, second));
        questions.create(question("sq_other", "session_2", "q_first", 0));
        assertEquals(List.of(first, second, last), questions.findBySessionId("session_1"));
        assertEquals("sq_other", questions.findBySessionIdAndQuestionId("session_2", "q_first").orElseThrow().id());
    }

    @Test void draftAndExplicitStateAreSavedTogetherWithoutCreatingAttemptAndCanBeCleared() {
        prepareQuestion();
        var draft = answer("opt_b");
        Instant savedAt = START.plusSeconds(1);
        questions.updateDraft("session_1", "q_original", draft, PracticeSessionQuestion.State.DRAFT, savedAt);
        var saved = findQuestion();
        assertEquals(draft, saved.draftAnswer());
        assertEquals(PracticeSessionQuestion.State.DRAFT, saved.practiceState());
        assertEquals(savedAt, saved.updatedAt());
        assertEquals(0, attempts.countBySessionQuestion("sq_1"));
        questions.updateDraft("session_1", "q_original", null, PracticeSessionQuestion.State.UNANSWERED, START.plusSeconds(2));
        assertNull(findQuestion().draftAnswer());
        assertEquals(PracticeSessionQuestion.State.UNANSWERED, findQuestion().practiceState());
        assertTrue(attempts.listBySessionQuestion("sq_1").isEmpty());
    }

    @Test void allCurrentStatesRoundTripAndRetryDoesNotOverwriteSubmittedFacts() {
        prepareQuestion();
        var first = attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL);
        attempts.append(first);
        for (var state : PracticeSessionQuestion.State.values()) {
            questions.updateState("session_1", "q_original", state, START.plusSeconds(1));
            assertEquals(state, findQuestion().practiceState());
            assertEquals(List.of(first), attempts.listBySessionQuestion("sq_1"));
        }
        questions.updateDraft("session_1", "q_original", answer("opt_b"), PracticeSessionQuestion.State.RETRYING,
                START.plusSeconds(2));
        assertEquals(PracticeSessionQuestion.State.RETRYING, findQuestion().practiceState());
        assertEquals(Optional.of(first), attempts.findLatest("sq_1"));
    }

    @Test void snapshotUpdatePreservesQuestionIdentityDraftAndAttempts() {
        prepareQuestion();
        var draft = answer("opt_a");
        questions.updateDraft("session_1", "q_original", draft, PracticeSessionQuestion.State.DRAFT, START.plusSeconds(1));
        var first = attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL);
        attempts.append(first);
        var changed = new PracticeSessionQuestion.Snapshot("SHORT_ANSWER", "为什么？",
                new PracticePayload(List.of()), new PracticePayload(Map.of("text", "参考答案")),
                "新解析", new PracticePayload(List.of()));
        Instant changedAt = START.plusSeconds(2);
        questions.updateSnapshot("session_1", "q_original", 9, changed, changedAt);
        var updated = findQuestion();
        assertEquals(changed, updated.snapshot());
        assertEquals("sq_1", updated.id());
        assertEquals("q_original", updated.questionId());
        assertEquals(9, updated.questionOrder());
        assertEquals(START, updated.createdAt());
        assertEquals(changedAt, updated.updatedAt());
        assertEquals(draft, updated.draftAnswer());
        assertEquals(PracticeSessionQuestion.State.DRAFT, updated.practiceState());
        assertEquals(List.of(first), attempts.listBySessionQuestion("sq_1"));
    }

    @Test void duplicateQuestionIdRollsBackEntireBatch() {
        sessions.create(session("session_1", "qb_1"));
        var first = question("sq_1", "session_1", "q_same", 0);
        var duplicate = question("sq_2", "session_1", "q_same", 1);
        assertPersistenceFailure(() -> questions.createAll(List.of(first, duplicate)));
        assertTrue(questions.findBySessionId("session_1").isEmpty());
    }

    @Test void failedSnapshotUpdateDoesNotPartiallyModifyRow() {
        prepareQuestion();
        var original = findQuestion();
        assertPersistenceFailure(() -> questions.updateSnapshot("session_1", "q_original", -1,
                snapshot("MULTIPLE_CHOICE", "非法顺序"), START.plusSeconds(1)));
        assertEquals(original, findQuestion());
    }

    @Test void allConnectionsEnableForeignKeysAndRejectMissingParents() throws Exception {
        for (int index = 0; index < 2; index++) {
            try (Connection connection = database.openConnection(); Statement query = connection.createStatement();
                    ResultSet row = query.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(row.next());
                assertEquals(1, row.getInt(1));
            }
        }
        assertPersistenceFailure(() -> questions.create(question("sq_1", "missing", "q_1", 0)));
        assertPersistenceFailure(() -> attempts.append(attempt("attempt_1", "missing", 1, QuestionAttempt.Mode.INITIAL)));
        assertEquals(0, rowCount("practice_session_question"));
        assertEquals(0, rowCount("question_attempt"));
    }

    @Test void attemptsAreOrderedByNumberNotInsertionOrSubmissionTimestamp() {
        prepareQuestion();
        var first = attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL);
        var second = attempt("attempt_2", "sq_1", 2, QuestionAttempt.Mode.RETRY);
        var third = attempt("attempt_3", "sq_1", 3, QuestionAttempt.Mode.REVISION);
        attempts.append(third);
        attempts.append(first);
        attempts.append(second);
        assertEquals(List.of(first, second, third), attempts.listBySessionQuestion("sq_1"));
        assertEquals(Optional.of(third), attempts.findLatest("sq_1"));
        assertEquals(3, attempts.countBySessionQuestion("sq_1"));
        assertEquals(4, attempts.nextAttemptNo("sq_1"));
        assertEquals(PracticeSessionQuestion.State.UNANSWERED, findQuestion().practiceState());
    }

    @Test void duplicateAttemptNumberFailsWithoutOverwritingPreviousAnswerOrResult() {
        prepareQuestion();
        var original = attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL);
        attempts.append(original);
        var duplicate = new QuestionAttempt("attempt_other", "sq_1", 1, QuestionAttempt.Mode.RETRY,
                answer("opt_b"), QuestionAttempt.Result.INCORRECT, null, null, START.plusSeconds(2));
        assertPersistenceFailure(() -> attempts.append(duplicate));
        assertEquals(List.of(original), attempts.listBySessionQuestion("sq_1"));
    }

    @Test void attemptsRoundTripAnswerResultsNullableScoresAndNanosecondTimeAfterReopen() {
        prepareQuestion();
        var correct = attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL);
        var incorrect = new QuestionAttempt("attempt_2", "sq_1", 2, QuestionAttempt.Mode.RETRY,
                answer("opt_b"), QuestionAttempt.Result.INCORRECT, 0.0, 1.0, START.plusNanos(1));
        var unscored = new QuestionAttempt("attempt_3", "sq_1", 3, QuestionAttempt.Mode.REVISION,
                new PracticePayload(Map.of("text", "输入型答案\n第二行")), QuestionAttempt.Result.UNSCORED,
                null, 5.5, START.plusNanos(2));
        attempts.append(correct);
        attempts.append(incorrect);
        attempts.append(unscored);
        var reopened = new SqliteQuestionAttemptRepository(new SqliteDatabase(directory));
        assertEquals(List.of(correct, incorrect, unscored), reopened.listBySessionQuestion("sq_1"));
        assertNull(reopened.listBySessionQuestion("sq_1").get(0).score());
        assertNull(reopened.listBySessionQuestion("sq_1").get(0).maxScore());
        assertEquals(0.0, reopened.listBySessionQuestion("sq_1").get(1).score());
    }

    @Test void nextAttemptNumberUsesMaximumNotCountWhenNumbersHaveGaps() {
        prepareQuestion();
        assertEquals(1, attempts.nextAttemptNo("sq_1"));
        attempts.append(attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL));
        attempts.append(attempt("attempt_3", "sq_1", 3, QuestionAttempt.Mode.RETRY));
        assertEquals(2, attempts.countBySessionQuestion("sq_1"));
        assertEquals(4, attempts.nextAttemptNo("sq_1"));
    }

    @Test void structuredAnswersRetainNestedNullsBooleansLargeIntegersAndExactDecimals() {
        prepareQuestion();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("text", "带引号\"与反斜线\\\n换行");
        nested.put("evaluation", null);
        nested.put("attributes", Arrays.asList(true, null, Map.of("precision",
                new BigDecimal("0.12345678901234567890123456789"),
                "large", new BigInteger("123456789012345678901234567890"))));
        var answer = new PracticePayload(nested);
        questions.updateDraft("session_1", "q_original", answer, PracticeSessionQuestion.State.REVISING, START);
        var submission = new QuestionAttempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.REVISION,
                answer, QuestionAttempt.Result.UNSCORED, null, null, START);
        attempts.append(submission);
        assertEquals(answer, findQuestion().draftAnswer());
        assertEquals(submission, attempts.findLatest("sq_1").orElseThrow());
    }

    @Test void deletingActiveQuestionCascadesOnlyItsAttempts() throws Exception {
        prepareQuestion();
        questions.create(question("sq_2", "session_1", "q_keep", 1));
        attempts.append(attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL));
        attempts.append(attempt("attempt_2", "sq_1", 2, QuestionAttempt.Mode.RETRY));
        var keep = attempt("attempt_keep", "sq_2", 1, QuestionAttempt.Mode.INITIAL);
        attempts.append(keep);
        questions.deleteBySessionIdAndQuestionId("session_1", "q_original");
        assertTrue(questions.findBySessionIdAndQuestionId("session_1", "q_original").isEmpty());
        assertTrue(attempts.listBySessionQuestion("sq_1").isEmpty());
        assertEquals(List.of(keep), attempts.listBySessionQuestion("sq_2"));
        assertEquals(1, rowCount("question_attempt"));
        assertNoForeignKeyViolations();
    }

    @Test void deletingArchivedSessionCascadesBothQuestionsAndAllThreeAttempts() throws Exception {
        prepareQuestion();
        questions.create(question("sq_2", "session_1", "q_second", 1));
        attempts.append(attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL));
        attempts.append(attempt("attempt_2", "sq_1", 2, QuestionAttempt.Mode.RETRY));
        attempts.append(attempt("attempt_3", "sq_2", 1, QuestionAttempt.Mode.INITIAL));
        sessions.archive("session_1", START.plusSeconds(3));
        sessions.deleteArchived("session_1");
        assertTrue(sessions.findById("session_1").isEmpty());
        assertTrue(questions.findBySessionId("session_1").isEmpty());
        assertEquals(0, rowCount("practice_session_question"));
        assertEquals(0, rowCount("question_attempt"));
        assertNoForeignKeyViolations();
    }

    @Test void deleteArchivedDoesNotDeleteActiveSessionAndQuestionDeleteDoesNotEraseArchive() {
        prepareQuestion();
        var first = attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL);
        attempts.append(first);
        sessions.deleteArchived("session_1");
        assertTrue(sessions.findById("session_1").isPresent());
        sessions.archive("session_1", START.plusSeconds(1));
        questions.deleteBySessionIdAndQuestionId("session_1", "q_original");
        assertTrue(questions.findBySessionIdAndQuestionId("session_1", "q_original").isPresent());
        assertEquals(List.of(first), attempts.listBySessionQuestion("sq_1"));
    }

    @Test void missingRecordsReturnEmptyResults() {
        assertTrue(sessions.findById("missing").isEmpty());
        assertTrue(sessions.findActiveByQuestionBankAssetId("missing").isEmpty());
        assertTrue(sessions.listArchivedByQuestionBankAssetId("missing").isEmpty());
        assertTrue(questions.findBySessionId("missing").isEmpty());
        assertTrue(questions.findBySessionIdAndQuestionId("missing", "missing").isEmpty());
        assertTrue(attempts.listBySessionQuestion("missing").isEmpty());
        assertTrue(attempts.findLatest("missing").isEmpty());
        assertEquals(0, attempts.countBySessionQuestion("missing"));
        assertEquals(1, attempts.nextAttemptNo("missing"));
    }

    @Test void migrationOnFreshDatabaseIsIdempotentAndCreatesSixSuccessfulVersions() throws Exception {
        new SqliteDatabase(directory);
        try (Connection connection = database.openConnection(); Statement query = connection.createStatement();
                ResultSet rows = query.executeQuery("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank")) {
            for (String version : List.of("1", "2", "3", "4", "5", "6")) {
                assertTrue(rows.next());
                assertEquals(version, rows.getString(1));
            }
            assertFalse(rows.next());
        }
        assertEquals(0, rowCount("practice_session"));
        assertEquals(0, rowCount("practice_session_question"));
        assertEquals(0, rowCount("question_attempt"));
    }

    @Test void upgradeFromV3PreservesExistingLegacyDataAndAddsV4ThroughV6() throws Exception {
        var oldDirectory = new QuizForgeDataDirectory(temporaryDirectory.resolve("v3-data"));
        String jdbcUrl = "jdbc:sqlite:" + oldDirectory.databaseFile();
        Flyway flyway = Flyway.configure().dataSource(jdbcUrl, "", "").target("3").load();
        assertEquals(3, flyway.migrate().migrationsExecuted);
        try (Connection connection = java.sql.DriverManager.getConnection(jdbcUrl);
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO workspace(id, name, created_at, updated_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, "existing_workspace");
            insert.setString(2, "旧工作区");
            insert.setString(3, START.toString());
            insert.setString(4, START.toString());
            insert.executeUpdate();
        }
        SqliteDatabase upgraded = new SqliteDatabase(oldDirectory);
        try (Connection connection = upgraded.openConnection(); Statement query = connection.createStatement()) {
            try (ResultSet row = query.executeQuery("SELECT name FROM workspace WHERE id = 'existing_workspace'")) {
                assertTrue(row.next());
                assertEquals("旧工作区", row.getString(1));
            }
            try (ResultSet row = query.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success = 1")) {
                assertTrue(row.next());
                assertEquals(6, row.getInt(1));
            }
        }
        var repository = new SqlitePracticeSessionRepository(upgraded);
        var newSession = session("upgraded_session", "qb_external");
        repository.create(newSession);
        assertEquals(Optional.of(newSession), repository.findById(newSession.id()));
    }

    @Test void databaseChecksRejectInvalidSessionQuestionAndAttemptEnums() throws Exception {
        prepareQuestion();
        attempts.append(attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL));
        try (Connection connection = database.openConnection(); Statement update = connection.createStatement()) {
            assertThrows(SQLException.class, () -> update.executeUpdate("UPDATE practice_session SET status = 'UNKNOWN'"));
            assertThrows(SQLException.class, () -> update.executeUpdate("UPDATE practice_session SET status = 'ARCHIVED'"));
            assertThrows(SQLException.class, () -> update.executeUpdate("UPDATE practice_session SET current_view = 'UNKNOWN'"));
            assertThrows(SQLException.class, () -> update.executeUpdate("UPDATE practice_session_question SET practice_state = 'UNKNOWN'"));
            assertThrows(SQLException.class, () -> update.executeUpdate("UPDATE question_attempt SET attempt_mode = 'UNKNOWN'"));
            assertThrows(SQLException.class, () -> update.executeUpdate("UPDATE question_attempt SET result = 'UNKNOWN'"));
            assertThrows(SQLException.class, () -> update.executeUpdate("UPDATE question_attempt SET attempt_no = 0"));
        }
    }

    @Test void corruptStoredJsonIsReportedAsPersistenceFailureWithoutChangingStoredFacts() throws Exception {
        prepareQuestion();
        attempts.append(attempt("attempt_1", "sq_1", 1, QuestionAttempt.Mode.INITIAL));
        try (Connection connection = database.openConnection();
                PreparedStatement update = connection.prepareStatement("UPDATE question_attempt SET answer_json = ? WHERE id = ?")) {
            update.setString(1, "{\"answer\":1,\"answer\":2}");
            update.setString(2, "attempt_1");
            update.executeUpdate();
        }
        assertPersistenceFailure(() -> attempts.listBySessionQuestion("sq_1"));
        assertEquals(1, attempts.countBySessionQuestion("sq_1"));
    }

    private PracticeSession session(String id, String bankId) {
        return new PracticeSession(id, bankId, BANK_REVISION, "Java 集合练习",
                PracticeSession.Status.ACTIVE, PracticeSession.View.QUESTION, "q_original", START, START, null);
    }

    private PracticeSessionQuestion question(String id, String sessionId, String questionId, int order) {
        return new PracticeSessionQuestion(id, sessionId, questionId, order,
                snapshot("SINGLE_CHOICE", "ArrayList 的底层结构？"),
                PracticeSessionQuestion.State.UNANSWERED, null, START, START);
    }

    private PracticeSessionQuestion.Snapshot snapshot(String type, String stem) {
        return new PracticeSessionQuestion.Snapshot(type, stem,
                new PracticePayload(List.of(Map.of("id", "opt_a", "content", "带\"引号\"、\\和换行\n第二行"),
                        Map.of("id", "opt_b", "content", "链表"))),
                new PracticePayload(Map.of("correctOptionIds", List.of("opt_a"))), "动态数组。\n解释。",
                new PracticePayload(List.of(Map.of("documentAssetId", "doc_1", "documentContentId", DOCUMENT_REVISION,
                        "anchorName", "定义", "occurrence", 2))));
    }

    private PracticePayload answer(String optionId) {
        return new PracticePayload(Map.of("selectedOptionIds", List.of(optionId)));
    }

    private QuestionAttempt attempt(String id, String sessionQuestionId, int no, QuestionAttempt.Mode mode) {
        return new QuestionAttempt(id, sessionQuestionId, no, mode, answer("opt_a"),
                QuestionAttempt.Result.CORRECT, null, null, START.plusSeconds(10 - no));
    }

    private void prepareQuestion() {
        sessions.create(session("session_1", "qb_1"));
        questions.create(question("sq_1", "session_1", "q_original", 0));
    }

    private PracticeSessionQuestion findQuestion() {
        return questions.findBySessionIdAndQuestionId("session_1", "q_original").orElseThrow();
    }

    private void assertPersistenceFailure(Runnable action) {
        var failure = assertThrows(QuizForgeException.class, action::run);
        assertEquals(ErrorCode.PERSISTENCE_FAILED, failure.code());
    }

    private long rowCount(String table) throws SQLException {
        try (Connection connection = database.openConnection(); Statement query = connection.createStatement();
                ResultSet row = query.executeQuery("SELECT count(*) FROM " + table)) {
            row.next();
            return row.getLong(1);
        }
    }

    private String cell(String sql, String id) throws SQLException {
        try (Connection connection = database.openConnection(); PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) {
                assertTrue(row.next());
                return row.getString(1);
            }
        }
    }

    private void assertNoForeignKeyViolations() throws SQLException {
        try (Connection connection = database.openConnection(); Statement query = connection.createStatement();
                ResultSet row = query.executeQuery("PRAGMA foreign_key_check")) {
            assertFalse(row.next());
        }
    }
}
