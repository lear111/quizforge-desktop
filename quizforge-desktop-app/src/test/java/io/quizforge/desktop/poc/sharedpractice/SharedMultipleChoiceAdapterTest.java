package io.quizforge.desktop.poc.sharedpractice;

import io.quizforge.core.practice.*;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.type.objective.choice.*;
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

class SharedMultipleChoiceAdapterTest {
    @TempDir Path directory;
    private QuestionBank bank() {
        var options=List.of("a","b","c","d").stream().map(id->new ChoiceOption("opt_"+id,new TextContent(id))).toList();
        var singleOptions=List.of("a","b","c","d").stream().map(id->new ChoiceOption("opt_single_"+id,new TextContent(id))).toList();
        return new QuestionBank("qb_multiple_contract","Multiple contract","2.0",List.of(),List.of(
            Question.choice("q_multiple","MULTIPLE_CHOICE",new TextContent("Select several"),new TextContent("Frozen analysis"),List.of(),new ChoicePayload(options),new ChoiceAnswerSpec(List.of("opt_a","opt_c"))),
            Question.choice("q_single","SINGLE_CHOICE",new TextContent("Select one"),null,List.of(),new ChoicePayload(singleOptions),new ChoiceAnswerSpec(List.of("opt_single_a")))),List.of());
    }
    @Test void multipleCoreRoundTripRetryAndHistoryPreserveTypeAnswerResultAndImmutableSnapshots() {
        var db=new SqliteDatabase(directory.resolve("practice.db"));var tx=new SqlitePracticeTransaction(db);
        var service=new PracticeSessionService(tx,Clock.systemUTC());var bank=bank();var revision=new QuestionBankV2Codec().contentId(bank);
        var adapter=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
        assertEquals("MULTIPLE_CHOICE",adapter.viewModel().question().type());assertEquals("MULTIPLE",adapter.viewModel().question().selectionMode());
        assertTrue(adapter.viewModel().question().selectedOptionIds().isEmpty());
        for(var answer:List.of(Set.of("opt_a"),Set.of("opt_a","opt_b"),Set.of("opt_b"))){
            assertEquals(answer,Set.copyOf(adapter.answerChanged(answer).question().selectedOptionIds()));
            var reopened=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
            assertEquals(answer,Set.copyOf(reopened.viewModel().question().selectedOptionIds()));
        }
        assertNull(adapter.viewModel().question().result());assertFalse(adapter.viewModelJson().contains("Frozen analysis"));
        assertTrue(adapter.viewModel().question().options().stream().allMatch(o->o.feedback()==SharedPracticeViewModel.Feedback.NONE));
        var firstDraft=DraftCanvasDocument.createEmpty();adapter.saveDraft(firstDraft);adapter.answerChanged(Set.of("opt_b","opt_d"));
        var first=adapter.submit().question().result();assertEquals("INCORRECT",first.status());assertEquals(0,first.score());
        var snapshots=new SqliteAttemptDraftSnapshotRepository(db);var frozen=snapshots.find(first.attemptId()).orElseThrow();
        assertEquals(firstDraft,frozen.document());adapter.retry();assertEquals(DraftCanvasDocument.createEmpty(),adapter.loadDraft());
        assertTrue(adapter.viewModel().question().selectedOptionIds().isEmpty());adapter.answerChanged(Set.of("opt_a","opt_c"));
        var secondDraft=new DraftCanvasDocument("1.0","1",new DraftCanvasDocument.Viewport(32,-19,1.5),new DraftCanvasDocument.QuestionCard(120,70,720),List.of());
        adapter.saveDraft(secondDraft);var second=adapter.submit().question().result();assertEquals("CORRECT",second.status());assertEquals(1,second.score());assertEquals("RETRY",second.attemptMode());
        assertEquals(frozen,snapshots.find(first.attemptId()).orElseThrow());assertEquals(secondDraft,snapshots.find(second.attemptId()).orElseThrow().document());
        var session=adapter.snapshot().session();new SqlitePracticeSessionRepository(db).archive(session.id(),Instant.now());
        var history=new PracticeHistoryService(tx);var detail=history.loadArchivedSessionDetail(bank.assetId(),session.id());
        var projection=new HistoryDraftAdapter(history,bank.assetId(),detail);var row=detail.questions().getFirst();
        var a=projection.load(row,row.attempts().getFirst());var b=projection.load(row,row.attempts().getLast());
        assertEquals("MULTIPLE_CHOICE",a.card().question().type());assertEquals("MULTIPLE",b.card().question().selectionMode());
        assertEquals(Set.of("opt_b","opt_d"),Set.copyOf(a.card().question().selectedOptionIds()));assertEquals(Set.of("opt_a","opt_c"),Set.copyOf(b.card().question().selectedOptionIds()));
        assertEquals(first.attemptId(),a.card().question().result().attemptId());assertEquals(second.attemptId(),b.card().question().result().attemptId());
        assertEquals(firstDraft,a.draft().document());assertEquals(secondDraft,b.draft().document());
    }
    @Test void coreRetainsSingleCardinalityAndRejectsUnknownMultipleOptionIds() {
        var tx=new SqlitePracticeTransaction(new SqliteDatabase(directory.resolve("validation.db")));var service=new PracticeSessionService(tx,Clock.systemUTC());var bank=bank();var revision=new QuestionBankV2Codec().contentId(bank);
        var state=service.openOrCreateActiveSession(bank,revision);String session=state.session().id();
        assertThrows(IllegalArgumentException.class,()->service.saveDraft(session,revision,"q_multiple",Set.of("unknown")));
        service.updateCurrentQuestion(session,revision,"q_single");var adapter=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
        assertEquals("SINGLE",adapter.viewModel().question().selectionMode());assertThrows(IllegalArgumentException.class,()->adapter.answerChanged(Set.of("opt_single_a","opt_single_c")));
        assertFalse(SharedPracticeViewModel.supportsType("READING"));
    }
}
