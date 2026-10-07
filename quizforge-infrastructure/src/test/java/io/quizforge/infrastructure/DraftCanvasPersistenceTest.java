package io.quizforge.infrastructure;

import io.quizforge.core.practice.*;
import io.quizforge.core.practice.draft.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQLite, real services, deliberately injected SQL failures. No Workspace creation. */
class DraftCanvasPersistenceTest {
    private static final String TYPE = "test.draft.persistence";
    @AfterEach void unregisterType() { QuestionTypes.unregister(TYPE); }
    @TempDir Path temp;
    SqliteDatabase db;
    SqlitePracticeTransaction tx;
    PracticeSessionService service;
    QuestionBank bank;
    ActivePracticeSnapshot opened;
    String sid, qid, psqid, revision;

    @BeforeEach void start() {
        QuestionTypes.register(new ExternalQuestionTypeDefinition(TYPE,"Draft test",QuestionTypeDefinition.Family.OBJECTIVE,"1.0.0",
                (operation,input)->switch(operation) {
                    case "createDraft" -> Map.of("prompt",Map.of("kind","TEXT","text","Prompt"),"payload",Map.of("statement","Example"),"answerSpec",Map.of("correct",true),"maxScore",1);
                    case "validate" -> Map.of("errors",List.of());
                    case "snapshot","targets" -> Map.of("targets",List.of(Map.of("id","decision","number",1)));
                    case "validateAnswer" -> Map.of("errors",List.of(),"empty",!ExternalQuestionTypeDefinition.object(input.get("answer")).containsKey("value"));
                    case "grade" -> Map.of("status","CORRECT","score",1,"maxScore",1);
                    default -> throw new IllegalArgumentException(operation);
                }));
        db = new SqliteDatabase(temp.resolve("practice.db")); tx = new SqlitePracticeTransaction(db);
        service = new PracticeSessionService(tx, Clock.systemUTC());
        var editor = new QuestionBankEditorModel(new QuestionBank("qb_draft", "Draft", List.of(), List.of(), List.of()));
        editor.addQuestion(TYPE); bank = editor.bank();
        revision = new QuestionBankV2Codec().contentId(bank); opened = service.openOrCreateActiveSession(bank, revision);
        sid = opened.session().id(); qid = bank.questions().getFirst().id(); psqid = opened.questions().getFirst().sessionQuestion().id();
    }
    static DraftCanvasDocument ink(String id) {
        return new DraftCanvasDocument("1.0", "1", new DraftCanvasDocument.Viewport(-17, 29, 1.4),
                new DraftCanvasDocument.QuestionCard(180, 95, 720), List.of(new DraftCanvasDocument.Stroke(id, "PEN", "#7054a5", 2.4,
                        List.of(new DraftCanvasDocument.Point(250, 155, .5), new DraftCanvasDocument.Point(350, 175, .8)))));
    }
    void save(DraftCanvasDocument document) { service.saveActiveDraftCanvas(sid, revision, qid, document); }
    void answer() { service.saveExtensionDraft(sid, revision, qid, new PracticePayload(Map.of("value",true))); }
    Optional<ActiveDraftCanvas> active() { return service.loadActiveDraftCanvas(sid, revision, qid); }
    QuestionAttempt submit() { return service.submitAnswer(sid, revision, qid).questions().getFirst().attempts().getLast(); }
    long count(String table) { return tx.execute(r -> { try(var c=db.openConnection();var st=c.createStatement();var rows=st.executeQuery("SELECT count(*) FROM " + table)) { rows.next(); return rows.getLong(1); } catch(SQLException e) { throw new RuntimeException(e); } }); }
    void sql(String text) throws Exception { try(var c=db.openConnection();var st=c.createStatement()) { st.execute(text); } }

    @Test void activeSaveReadOverwriteAndNewDatabaseInstanceRestoreAllGeometry() {
        assertTrue(active().isEmpty()); save(ink("A")); assertEquals(ink("A"), active().orElseThrow().document());
        save(ink("B")); assertEquals(1, count("practice_draft_canvas"));
        var reopened = new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(temp.resolve("practice.db"))), Clock.systemUTC());
        var again = reopened.openOrCreateActiveSession(bank, revision);
        assertEquals(sid, again.session().id());
        assertEquals(ink("B"), reopened.loadActiveDraftCanvas(sid, revision, qid).orElseThrow().document());
        assertTrue(again.questions().getFirst().attempts().isEmpty());
    }
    @Test void submitAtomicallyCreatesAttemptSnapshotAndRemovesActive() {
        save(ink("A")); answer(); var attempt = submit();
        assertTrue(active().isEmpty()); assertEquals(1, count("question_attempt")); assertEquals(1, count("attempt_draft_snapshot"));
        var snapshot = service.findAttemptDraftSnapshot(attempt.id()).orElseThrow();
        assertEquals(attempt.submittedAt(), snapshot.createdAt()); assertEquals(ink("A"), snapshot.document());
        assertThrows(IllegalStateException.class, () -> save(ink("illegal")));
    }
    @Test void retryAndSecondSubmitCreateIndependentSnapshotsAndKeepOldAttemptUnchanged() {
        save(ink("A")); answer(); var first = submit(); var frozen = service.findAttemptDraftSnapshot(first.id()).orElseThrow();
        var retry = service.retryQuestion(sid, revision, qid);
        assertEquals(PracticeSessionQuestion.State.RETRYING, retry.questions().getFirst().sessionQuestion().practiceState());
        assertNull(retry.questions().getFirst().sessionQuestion().draftAnswer()); assertTrue(active().isEmpty());
        assertEquals(first, retry.questions().getFirst().attempts().getFirst());
        save(ink("B")); answer(); var second = submit();
        assertEquals(QuestionAttempt.Mode.INITIAL, first.attemptMode()); assertEquals(QuestionAttempt.Mode.RETRY, second.attemptMode());
        assertNotEquals(first.id(), second.id()); assertTrue(active().isEmpty());
        assertEquals(frozen, service.findAttemptDraftSnapshot(first.id()).orElseThrow());
        assertEquals(ink("B"), service.findAttemptDraftSnapshot(second.id()).orElseThrow().document());
    }
    @Test void snapshotInsertFailureRollsBackAttemptAndKeepsActiveAndAnswer() throws Exception {
        save(ink("A")); answer();
        sql("CREATE TRIGGER injected_snapshot BEFORE INSERT ON attempt_draft_snapshot BEGIN SELECT RAISE(ABORT, 'snapshot failure'); END");
        assertThrows(RuntimeException.class, this::submit); assertEquals(0, count("question_attempt")); assertEquals(0, count("attempt_draft_snapshot"));
        assertEquals(ink("A"), active().orElseThrow().document());
        assertEquals(PracticeSessionQuestion.State.DRAFT, service.openOrCreateActiveSession(bank, revision).questions().getFirst().sessionQuestion().practiceState());
    }
    @Test void activeDeleteFailureRollsBackEntireSubmitIncludingFrozenSnapshot() throws Exception {
        save(ink("A")); answer();
        sql("CREATE TRIGGER injected_delete BEFORE DELETE ON practice_draft_canvas BEGIN SELECT RAISE(ABORT, 'delete failure'); END");
        assertThrows(RuntimeException.class, this::submit); assertEquals(0, count("question_attempt")); assertEquals(0, count("attempt_draft_snapshot"));
        assertEquals(ink("A"), active().orElseThrow().document());
        assertEquals(PracticeSessionQuestion.State.DRAFT, service.openOrCreateActiveSession(bank, revision).questions().getFirst().sessionQuestion().practiceState());
    }
    @Test void draftSaveFailureCannotOverwriteDurableDocument() throws Exception {
        save(ink("A"));
        sql("CREATE TRIGGER injected_save BEFORE UPDATE ON practice_draft_canvas BEGIN SELECT RAISE(ABORT, 'save failure'); END");
        assertThrows(RuntimeException.class, () -> save(ink("B"))); assertEquals(ink("A"), active().orElseThrow().document());
    }
    @Test void submitWithoutActiveDraftCreatesNoForcedEmptySnapshotAndLegacyAttemptRemainsReadable() {
        answer(); var attempt = submit(); assertTrue(service.findAttemptDraftSnapshot(attempt.id()).isEmpty());
        assertEquals(1, count("question_attempt")); assertTrue(active().isEmpty());
        assertEquals(attempt, new SqliteQuestionAttemptRepository(db).findLatest(psqid).orElseThrow());
    }
    @Test void frozenRepositoryRejectsOverwriteAndDatabaseRejectsUpdate() throws Exception {
        save(ink("A")); answer(); var attempt = submit(); var frozen = service.findAttemptDraftSnapshot(attempt.id()).orElseThrow();
        var repository = new SqliteAttemptDraftSnapshotRepository(db);
        assertThrows(RuntimeException.class, () -> repository.append(new AttemptDraftSnapshot(attempt.id(), ink("B"), Instant.now())));
        assertThrows(SQLException.class, () -> sql("UPDATE attempt_draft_snapshot SET document_json='{}'"));
        assertEquals(frozen, repository.find(attempt.id()).orElseThrow());
    }
    @Test void questionCascadeRemovesActiveAttemptsAndSnapshots() throws Exception {
        save(ink("A")); answer(); submit(); service.retryQuestion(sid, revision, qid); save(ink("B"));
        sql("DELETE FROM practice_session_question WHERE id='" + psqid + "'");
        assertEquals(0, count("practice_draft_canvas")); assertEquals(0, count("question_attempt")); assertEquals(0, count("attempt_draft_snapshot"));
    }
    @Test void attemptDeletionCascadesOnlyItsSnapshot() throws Exception {
        save(ink("A")); answer(); var first = submit(); service.retryQuestion(sid, revision, qid); save(ink("B")); answer(); var second = submit();
        sql("DELETE FROM question_attempt WHERE id='" + first.id() + "'");
        assertTrue(service.findAttemptDraftSnapshot(first.id()).isEmpty());
        assertEquals(ink("B"), service.findAttemptDraftSnapshot(second.id()).orElseThrow().document());
    }
    @Test void foreignKeysRejectOrphanDraftAndSnapshot() {
        assertThrows(RuntimeException.class, () -> new SqliteActiveDraftCanvasRepository(db).save(new ActiveDraftCanvas("missing", ink("A"), Instant.now())));
        assertThrows(RuntimeException.class, () -> new SqliteAttemptDraftSnapshotRepository(db).append(new AttemptDraftSnapshot("missing", ink("A"), Instant.now())));
    }
    @Test void saveRequiresActiveSessionMatchingRevisionCurrentAndExistingQuestion() {
        assertThrows(IllegalStateException.class, () -> service.saveActiveDraftCanvas(sid, "stale", qid, ink("A")));
        assertThrows(IllegalStateException.class, () -> service.saveActiveDraftCanvas(sid, revision, "missing", ink("A")));
        service.updateCurrentView(sid, revision, PracticeSession.View.SUMMARY);
        assertThrows(IllegalStateException.class, () -> save(ink("A")));
        service.updateCurrentQuestion(sid, revision, qid); service.restartPractice(sid, revision, bank, revision);
        assertThrows(IllegalStateException.class, () -> save(ink("A")));
        assertEquals(0, count("practice_draft_canvas"));
    }
    @Test void codecRejectsUnknownFieldsVersionsMalformedDuplicateAndCoercedValues() {
        var codec = new DraftCanvasJsonCodec(); var text = codec.encode(ink("A"));
        assertEquals(ink("A"), DraftCanvasJsonCodec.decode(text));
        for (String bad : List.of("{", text + " {}", text.replace("\"layoutVersion\":\"1\"", "\"layoutVersion\":\"2\""),
                text.replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":\"future\""),
                text.replaceFirst("\\{", "{\"unexpected\":42,"), text.replace("\"zoom\":1.4", "\"zoom\":\"1.4\"")))
            assertThrows(IllegalArgumentException.class, () -> DraftCanvasJsonCodec.decode(bad), bad);
    }
    @Test void codecKeepsLegacyShapeAndRoundTripsOptionalTextAndPaper() {
        var codec = new DraftCanvasJsonCodec();
        var legacy = ink("A"); var legacyJson = codec.encode(legacy);
        assertFalse(legacyJson.contains("\"texts\"")); assertFalse(legacyJson.contains("\"paper\""));
        assertEquals(legacy, DraftCanvasJsonCodec.decode(legacyJson));
        var note = new DraftCanvasDocument.TextAnnotation("note", 15, -20, 240, 18, "#333", "notes\n草稿");
        for (var pattern : DraftCanvasDocument.PaperPattern.values()) {
            var paper = new DraftCanvasDocument.Paper("#fff8dc", pattern);
            var document = new DraftCanvasDocument("1.0", "1", legacy.viewport(), legacy.questionCard(), legacy.strokes(), List.of(note), paper);
            var encoded = codec.encode(document);
            assertEquals(document, DraftCanvasJsonCodec.decode(encoded));
            for (String bad : List.of(encoded.replace("\"pattern\":\"" + pattern + "\"", "\"pattern\":\"UNKNOWN\""),
                    encoded.replace("\"size\":18.0", "\"size\":\"18\""),
                    encoded.replace("\"text\":\"notes", "\"extra\":1,\"text\":\"notes")))
                assertThrows(IllegalArgumentException.class, () -> DraftCanvasJsonCodec.decode(bad), bad);
        }
        save(new DraftCanvasDocument("1.0", "1", legacy.viewport(), legacy.questionCard(), legacy.strokes(), List.of(note),
                new DraftCanvasDocument.Paper("#fff8dc", DraftCanvasDocument.PaperPattern.GRID)));
        assertEquals(List.of(note), active().orElseThrow().document().texts());
    }
    @Test void migrationFromV5PreservesLegacyRowsAndChecksums() throws Exception {
        var file = temp.resolve("v5.db"); var url = "jdbc:sqlite:" + file;
        Flyway.configure().dataSource(url, "", "").target("5").load().migrate();
        var checksums = new ArrayList<String>();
        try(var c=java.sql.DriverManager.getConnection(url);var st=c.createStatement()) {
            st.execute("INSERT INTO workspace(id,name,created_at,updated_at) VALUES('legacy','Keep','2026-10-03T00:00:00Z','2026-10-03T00:00:00Z')");
            try(var rows=st.executeQuery("SELECT version || ':' || checksum FROM flyway_schema_history ORDER BY installed_rank")) { while(rows.next()) checksums.add(rows.getString(1)); }
        }
        var upgraded = new SqliteDatabase(file);
        try(var c=upgraded.openConnection();var st=c.createStatement()) {
            try(var rows=st.executeQuery("SELECT version || ':' || checksum FROM flyway_schema_history WHERE version != '6' ORDER BY installed_rank")) {
                var after=new ArrayList<String>(); while(rows.next()) after.add(rows.getString(1)); assertEquals(checksums,after);
            }
            try(var rows=st.executeQuery("SELECT name FROM workspace WHERE id='legacy'")) { assertTrue(rows.next()); assertEquals("Keep",rows.getString(1)); }
            try(var rows=st.executeQuery("SELECT count(*) FROM attempt_draft_snapshot")) { rows.next(); assertEquals(0,rows.getInt(1)); }
        }
    }
}
