package io.quizforge.desktop.poc.sharedpractice;

import io.quizforge.core.practice.*;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.type.objective.choice.*;
import io.quizforge.core.question.type.objective.cloze.*;
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

class SharedClozeAdapterTest {
    @TempDir Path directory;
    QuestionBank bank() {
        var items=List.of(1,2).stream().map(n->new ClozeBlank("blank_"+n,n,
                List.of("a","b","c","d").stream().map(id->new ChoiceOption("opt_"+n+"_"+id,new TextContent(id))).toList())).toList();
        var q=new Question("q_reading","CLOZE",List.of(),new TextContent("Passage {{1}} repeated {{1}} then {{2}}"),new ClozePayload(items),
                new ClozeAnswerSpec(List.of(new ClozeAnswerSpec.Answer("blank_1","opt_1_a"),new ClozeAnswerSpec.Answer("blank_2","opt_2_c"))),
                new ScoreSpec(java.math.BigDecimal.valueOf(2)),null,new TextContent("Frozen analysis"),List.of());
        return new QuestionBank("qb_reading_contract","Reading contract","2.0",List.of(),List.of(q),List.of());
    }
    @Test void readingCoreRoundTripRetryAndHistoryPreserveParentChildrenAnswerAndImmutableSnapshots() {
        var db=new SqliteDatabase(directory.resolve("practice.db"));var tx=new SqlitePracticeTransaction(db);
        var service=new PracticeSessionService(tx,Clock.systemUTC());var bank=bank();var revision=new QuestionBankV2Codec().contentId(bank);
        var adapter=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
        assertEquals("CLOZE",adapter.viewModel().question().type());assertEquals("COMPOSITE_SINGLE",adapter.viewModel().question().selectionMode());
        assertTrue(adapter.viewModel().question().selectedOptionIds().isEmpty());
        var presentation=(SharedPracticeViewModel.ClozePresentation)adapter.viewModel().question().presentation();
        assertEquals(List.of("blank_1","blank_2"),presentation.items().stream().map(SharedPracticeViewModel.ReadingItem::id).toList());
        String parent=adapter.viewModel().question().sessionQuestionId();
        for(var answer:List.of(Set.of("opt_1_a"),Set.of("opt_1_a","opt_2_b"),Set.of("opt_1_b"))){
            assertEquals(answer,Set.copyOf(adapter.answerChanged(answer).question().selectedOptionIds()));
            var reopened=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
            assertEquals(answer,Set.copyOf(reopened.viewModel().question().selectedOptionIds()));
            assertEquals(parent,reopened.viewModel().question().sessionQuestionId());
        }
        assertNull(adapter.viewModel().question().result());assertFalse(adapter.viewModelJson().contains("Frozen analysis"));
        assertTrue(adapter.viewModel().question().options().stream().allMatch(o->o.feedback()==SharedPracticeViewModel.Feedback.NONE));
        var firstDraft=DraftCanvasDocument.createEmpty();adapter.saveDraft(firstDraft);adapter.answerChanged(Set.of("opt_1_b","opt_2_d"));
        var first=adapter.submit().question().result();assertEquals("INCORRECT",first.status());assertEquals(0,first.score());
        var snapshots=new SqliteAttemptDraftSnapshotRepository(db);var frozen=snapshots.find(first.attemptId()).orElseThrow();
        assertEquals(firstDraft,frozen.document());adapter.retry();assertEquals(DraftCanvasDocument.createEmpty(),adapter.loadDraft());
        assertTrue(adapter.viewModel().question().selectedOptionIds().isEmpty());adapter.answerChanged(Set.of("opt_1_a","opt_2_c"));
        var secondDraft=new DraftCanvasDocument("1.0","1",new DraftCanvasDocument.Viewport(32,-19,1.5),new DraftCanvasDocument.QuestionCard(120,70,720),List.of());
        adapter.saveDraft(secondDraft);var second=adapter.submit().question().result();assertEquals("CORRECT",second.status());assertEquals(4,second.score());assertEquals("RETRY",second.attemptMode());
        assertEquals(frozen,snapshots.find(first.attemptId()).orElseThrow());assertEquals(secondDraft,snapshots.find(second.attemptId()).orElseThrow().document());
        var session=adapter.snapshot().session();new SqlitePracticeSessionRepository(db).archive(session.id(),Instant.now());
        var history=new PracticeHistoryService(tx);var detail=history.loadArchivedSessionDetail(bank.assetId(),session.id());
        var projection=new HistoryDraftAdapter(history,bank.assetId(),detail);var row=detail.questions().getFirst();
        var a=projection.load(row,row.attempts().getFirst());var b=projection.load(row,row.attempts().getLast());
        assertEquals("CLOZE",a.card().question().type());assertEquals("COMPOSITE_SINGLE",b.card().question().selectionMode());
        assertEquals(Set.of("opt_1_b","opt_2_d"),Set.copyOf(a.card().question().selectedOptionIds()));assertEquals(Set.of("opt_1_a","opt_2_c"),Set.copyOf(b.card().question().selectedOptionIds()));
        assertEquals(first.attemptId(),a.card().question().result().attemptId());assertEquals(second.attemptId(),b.card().question().result().attemptId());
        assertEquals(firstDraft,a.draft().document());assertEquals(secondDraft,b.draft().document());
    }

}
