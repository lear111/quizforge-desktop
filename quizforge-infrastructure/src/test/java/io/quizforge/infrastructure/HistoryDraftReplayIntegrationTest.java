package io.quizforge.infrastructure;

import io.quizforge.core.port.*;
import io.quizforge.core.practice.*;
import io.quizforge.core.practice.draft.*;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.*;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Archived reads against real SQLite; mutable draft access is a failing capability. */
class HistoryDraftReplayIntegrationTest {
    @TempDir Path temp;
    SqliteDatabase db;
    SqlitePracticeTransaction tx;
    PracticeSessionService practice;
    PracticeHistoryService history;
    QuestionBank bank;
    String sid, qid, psqid, revision;

    @BeforeEach void setup() {
        db=new SqliteDatabase(temp.resolve("history.db"));tx=new SqlitePracticeTransaction(db);
        practice=new PracticeSessionService(tx,Clock.systemUTC());
        var editor=new QuestionBankEditorModel(new QuestionBank("qb_replay","Frozen title",List.of(),List.of(),List.of()));
        editor.addQuestion("SINGLE_CHOICE");editor.addQuestion("SINGLE_CHOICE");bank=editor.bank();
        revision=new QuestionBankV2Codec().contentId(bank);
        var opened=practice.openOrCreateActiveSession(bank,revision);sid=opened.session().id();
        qid=bank.questions().getFirst().id();psqid=opened.questions().getFirst().sessionQuestion().id();
        var forbidden=new ActiveDraftCanvasRepository(){
            public Optional<ActiveDraftCanvas> find(String id){throw new AssertionError("History read Active Draft");}
            public void save(ActiveDraftCanvas draft){throw new AssertionError("History wrote Active Draft");}
            public void delete(String id){throw new AssertionError("History deleted Active Draft");}
        };
        history=new PracticeHistoryService(new PracticeTransaction(){
            public <T>T execute(Function<Repositories,T> operation){
                return tx.execute(r->operation.apply(new Repositories(r.sessions(),r.questions(),r.attempts(),forbidden,r.draftSnapshots())));
            }
        });
    }
    static DraftCanvasDocument ink(String name){return DraftCanvasPersistenceTest.ink(name);}
    QuestionAttempt submit(DraftCanvasDocument document,int option){
        if(document!=null)practice.saveActiveDraftCanvas(sid,revision,qid,document);
        practice.saveDraft(sid,revision,qid,Set.of(bank.questions().getFirst().choicePayload().options().get(option).id()));
        return practice.submitAnswer(sid,revision,qid).questions().getFirst().attempts().getLast();
    }
    void archive(){new SqlitePracticeSessionRepository(db).archive(sid,Instant.now());}
    HistoryDraftReplay read(String attempt){return history.loadDraftReplay(bank.assetId(),sid,psqid,attempt);}
    List<String> rows() throws Exception {
        var result=new ArrayList<String>();
        try(var c=db.openConnection();var s=c.createStatement()){
            for(String table:List.of("practice_draft_canvas","attempt_draft_snapshot"))
                try(var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1")){
                    while(r.next()){var row=new StringBuilder(table);for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.append('|').append(r.getString(i));result.add(row.toString());}
                }
        }
        return result;
    }
    @Test void attemptIdentityRestoresIndependentFrozenGeometryAndNeverReadsOrWritesActive() throws Exception {
        var first=submit(ink("A"),0);practice.retryQuestion(sid,revision,qid);
        var second=submit(ink("B"),1);practice.retryQuestion(sid,revision,qid);
        practice.saveActiveDraftCanvas(sid,revision,qid,ink("active"));archive();
        var before=rows();var detail=history.loadArchivedSessionDetail(bank.assetId(),sid);
        var attempts=detail.questions().getFirst().attempts();
        assertEquals(List.of(first.id(),second.id()),attempts.stream().map(PracticeHistoryDetail.Attempt::attemptId).toList());
        for(int i=0;i<3;i++){
            assertEquals(ink("A"),read(first.id()).document());assertEquals(ink("B"),read(second.id()).document());
        }
        assertEquals(first.answer(),attempts.getFirst().answer());assertEquals(second.answer(),attempts.getLast().answer());
        assertEquals(first.score(),attempts.getFirst().score());assertEquals(before,rows());
    }
    @Test void lookupRejectsForeignAttemptQuestionBankAndActiveSession(){
        var first=submit(ink("A"),0);
        assertThrows(IllegalStateException.class,()->read(first.id()));archive();
        assertThrows(IllegalArgumentException.class,()->read("foreign-attempt"));
        assertThrows(IllegalArgumentException.class,()->history.loadDraftReplay(bank.assetId(),sid,"foreign-question",first.id()));
        assertThrows(IllegalStateException.class,()->history.loadDraftReplay("foreign-bank",sid,psqid,first.id()));
        String other=history.loadArchivedSessionDetail(bank.assetId(),sid).questions().getLast().sessionQuestionId();
        assertThrows(IllegalArgumentException.class,()->history.loadDraftReplay(bank.assetId(),sid,other,first.id()));
    }
    @Test void oldHistoryWithoutSnapshotKeepsAnswerAndReturnsMissingWithoutFakeDocument(){
        var old=submit(null,0);archive();
        var detail=history.loadArchivedSessionDetail(bank.assetId(),sid);
        assertEquals(old.answer(),detail.questions().getFirst().attempts().getFirst().answer());
        assertEquals(HistoryDraftReplay.Status.MISSING,read(old.id()).status());assertNull(read(old.id()).document());
        assertEquals(HistoryDraftReplay.Status.MISSING,read(null).status());
    }
    @ParameterizedTest @ValueSource(strings={"schema","layout","malformed","unknown"})
    void unsupportedSnapshotsRemainByteIdenticalAndResultStillLoads(String type) throws Exception {
        var attempt=submit(null,0);archive();
        String good=new DraftCanvasJsonCodec().encode(ink("future"));
        String bad=switch(type){case "schema"->good.replace("\"schemaVersion\":\"1.0\"","\"schemaVersion\":\"9.0\"");case "layout"->good.replace("\"layoutVersion\":\"1\"","\"layoutVersion\":\"9\"");case "unknown"->"{\"future\":42,"+good.substring(1);default->"{";};
        try(var c=db.openConnection();var insert=c.prepareStatement("INSERT INTO attempt_draft_snapshot(attempt_id,document_json,created_at) VALUES(?,?,?)")){
            insert.setString(1,attempt.id());insert.setString(2,bad);insert.setString(3,attempt.submittedAt().toString());insert.executeUpdate();
        }
        var before=rows();var replay=read(attempt.id());
        assertEquals(HistoryDraftReplay.Status.UNAVAILABLE,replay.status());assertNull(replay.document());assertFalse(replay.message().isBlank());
        assertEquals(attempt.answer(),history.loadArchivedSessionDetail(bank.assetId(),sid).questions().getFirst().attempts().getFirst().answer());
        assertEquals(before,rows());
    }
}
