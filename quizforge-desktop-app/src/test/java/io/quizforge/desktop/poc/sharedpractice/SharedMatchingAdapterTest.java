package io.quizforge.desktop.poc.sharedpractice;

import io.quizforge.core.practice.*;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.type.objective.choice.*;
import io.quizforge.core.question.type.objective.matching.*;
import io.quizforge.desktop.ui.question.history.HistoryDraftAdapter;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.*;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SharedMatchingAdapterTest {
    @TempDir Path directory;
    QuestionBank bank() {
        var slots=java.util.stream.IntStream.rangeClosed(1,8).mapToObj(n->new MatchingBlank("blank_m_"+n,n,n==1||n==4||n==6)).toList();
        var options=java.util.stream.IntStream.rangeClosed(1,8).mapToObj(n->new MatchingOption("opt_m_"+(char)('a'+n-1),String.valueOf((char)('A'+n-1)))).toList();
        var answers=java.util.stream.IntStream.rangeClosed(1,8).mapToObj(n->new MatchingAnswerSpec.Answer("blank_m_"+n,"opt_m_"+(char)('a'+n-1))).toList();
        var q=new Question("q_matching","MATCHING",List.of(),new TextContent("A–H paragraphs. Order them."),new MatchingPayload(slots,options),new MatchingAnswerSpec(answers),new ScoreSpec(java.math.BigDecimal.valueOf(2)),null,new TextContent("Frozen analysis"),List.of());
        return new QuestionBank("qb_matching_contract","Matching contract","2.0",List.of(),List.of(q),List.of());
    }
    @Test void readingCoreRoundTripRetryAndHistoryPreserveParentChildrenAnswerAndImmutableSnapshots() {
        var db=new SqliteDatabase(directory.resolve("practice.db"));var tx=new SqlitePracticeTransaction(db);
        var service=new PracticeSessionService(tx,Clock.systemUTC());var bank=bank();var revision=new QuestionBankV2Codec().contentId(bank);
        var adapter=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
        assertEquals("MATCHING",adapter.viewModel().question().type());
        assertEquals("ASSIGNMENT",adapter.viewModel().question().selectionMode());
        var p=(SharedPracticeViewModel.MatchingPresentation)adapter.viewModel().question().presentation();
        assertEquals(8,p.slots().size());assertEquals(3,p.slots().stream().filter(SharedPracticeViewModel.MatchingSlot::locked).count());
        assertEquals("opt_m_a",p.slots().getFirst().givenOptionId());assertNull(p.slots().get(1).correctOptionId());
        String parent=adapter.viewModel().question().sessionQuestionId();
        for(var answer:List.of(Map.of("blank_m_2","opt_m_b"),Map.of("blank_m_2","opt_m_b","blank_m_3","opt_m_b"),Map.of("blank_m_3","opt_m_c"))){
            adapter.assignmentsChanged(answer);var reopened=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
            assertEquals(answer,((SharedPracticeViewModel.MatchingPresentation)reopened.viewModel().question().presentation()).assignments());assertEquals(parent,reopened.viewModel().question().sessionQuestionId());
        }
        assertThrows(IllegalArgumentException.class,()->adapter.assignmentsChanged(Map.of("blank_m_1","opt_m_b")));
        assertThrows(IllegalArgumentException.class,()->adapter.assignmentsChanged(Map.of("blank_m_2","opt_m_a")));
        assertNull(adapter.viewModel().question().result());assertFalse(adapter.viewModelJson().contains("Frozen analysis"));
        assertTrue(adapter.viewModel().question().options().stream().allMatch(o->o.feedback()==SharedPracticeViewModel.Feedback.NONE));
        var firstDraft=DraftCanvasDocument.createEmpty();adapter.saveDraft(firstDraft);adapter.assignmentsChanged(Map.of("blank_m_2","opt_m_c"));
        var first=adapter.submit().question().result();assertEquals("INCORRECT",first.status());assertEquals(0,first.score());
        var snapshots=new SqliteAttemptDraftSnapshotRepository(db);var frozen=snapshots.find(first.attemptId()).orElseThrow();
        assertEquals(firstDraft,frozen.document());adapter.retry();assertEquals(DraftCanvasDocument.createEmpty(),adapter.loadDraft());
        assertTrue(adapter.viewModel().question().selectedOptionIds().isEmpty());adapter.assignmentsChanged(Map.of("blank_m_2","opt_m_b","blank_m_3","opt_m_c","blank_m_5","opt_m_e","blank_m_7","opt_m_g","blank_m_8","opt_m_h"));
        var secondDraft=new DraftCanvasDocument("1.0","1",new DraftCanvasDocument.Viewport(32,-19,1.5),new DraftCanvasDocument.QuestionCard(120,70,720),List.of());
        adapter.saveDraft(secondDraft);var second=adapter.submit().question().result();assertEquals("CORRECT",second.status());assertEquals(10,second.score());assertEquals("RETRY",second.attemptMode());
        assertEquals(frozen,snapshots.find(first.attemptId()).orElseThrow());assertEquals(secondDraft,snapshots.find(second.attemptId()).orElseThrow().document());
        var session=adapter.snapshot().session();new SqlitePracticeSessionRepository(db).archive(session.id(),Instant.now());
        var history=new PracticeHistoryService(tx);var detail=history.loadArchivedSessionDetail(bank.assetId(),session.id());
        var projection=new HistoryDraftAdapter(history,bank.assetId(),detail);var row=detail.questions().getFirst();
        var a=projection.load(row,row.attempts().getFirst());var b=projection.load(row,row.attempts().getLast());
        assertEquals("MATCHING",a.card().question().type());assertEquals("ASSIGNMENT",b.card().question().selectionMode());
        assertEquals(Map.of("blank_m_2","opt_m_c"),((SharedPracticeViewModel.MatchingPresentation)a.card().question().presentation()).assignments());assertEquals(5,((SharedPracticeViewModel.MatchingPresentation)b.card().question().presentation()).assignments().size());
        assertEquals(first.attemptId(),a.card().question().result().attemptId());assertEquals(second.attemptId(),b.card().question().result().attemptId());
        assertEquals(firstDraft,a.draft().document());assertEquals(secondDraft,b.draft().document());
    }

}
