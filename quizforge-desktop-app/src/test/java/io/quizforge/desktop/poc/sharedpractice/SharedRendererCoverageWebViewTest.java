package io.quizforge.desktop.poc.sharedpractice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.practice.*;
import io.quizforge.desktop.testing.FxTestRuntime;
import io.quizforge.desktop.ui.question.history.*;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.*;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real WebKit + Core + SQLite + immutable Attempt history, with one lifecycle per registered type. */
class SharedRendererCoverageWebViewTest {
    static final ObjectMapper JSON=new ObjectMapper();
    @TempDir Path directory;
    @BeforeAll static void startFx() throws Exception {FxTestRuntime.start();}
    @ParameterizedTest @ValueSource(strings={"READING","CLOZE","MATCHING"})
    void activeIntentRestoreFinalStrokeBarrierRetryHistoryAndCleanup(String type) throws Exception {
        var bank=switch(type){case "READING"->new SharedReadingAdapterTest().bank();case "CLOZE"->new SharedClozeAdapterTest().bank();default->new SharedMatchingAdapterTest().bank();};
        boolean matching=type.equals("MATCHING");
        var db=new SqliteDatabase(directory.resolve("practice.db"));var tx=new SqlitePracticeTransaction(db);
        var service=new PracticeSessionService(tx,Clock.systemUTC());var revision=new QuestionBankV2Codec().contentId(bank);
        var adapter=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
        var canvas=fx(()->new SharedPracticeCanvasWebView(adapter));var stage=fx(()->open(canvas.view()));
        try {
            canvas.ready().toCompletableFuture().get(30,TimeUnit.SECONDS);
            var engine=canvas.view();String target=matching?"blank_m_2":type.equals("CLOZE")?"blank_2":"item_2";String parent=adapter.viewModel().question().sessionQuestionId();
            assertEquals(type,script(engine,"window.sharedPractice.getViewState().question.type"));
            // Exercise the actual outline dispatch, including CLOZE, rather than only the WebView API.
            var routed=new java.util.concurrent.atomic.AtomicReference<String>();
            fx(()->{
                var outline=new io.quizforge.desktop.ui.question.shared.QuestionOutlineView(
                        new QuestionBankPracticeSession(bank),ignored->fail("Composite cell must route its child"));
                outline.setItemJump((parentIndex,number)->routed.set(parentIndex+":"+number));
                var scene=new Scene(outline);outline.applyCss();outline.layout();
                ((javafx.scene.control.Button)scene.lookup("#question-number-2")).fire();
                return null;
            });
            assertEquals(matching?"0:3":"0:2",routed.get());
            assertEquals(8,((Number)script(engine,"document.querySelectorAll('.composite-item input,.assignment-slots select').length")).intValue());
            assertTrue(fx(()->canvas.focusTarget(target)));
            assertEquals(parent,adapter.viewModel().question().sessionQuestionId());
            assertEquals(target,script(engine,"document.activeElement.dataset.targetId"));
            if(matching){assign(engine,"blank_m_2","opt_m_b");waitFor(()->((SharedPracticeViewModel.MatchingPresentation)adapter.viewModel().question().presentation()).assignments().size()==1);}else{choose(engine,"opt_1_a");waitFor(()->adapter.viewModel().question().selectedOptionIds().contains("opt_1_a"));}
            assertEquals("DRAFT",adapter.viewModel().question().state().name());
            if(type.equals("CLOZE"))assertEquals("opt_1_a|opt_1_a|",script(engine,"Array.from(document.querySelectorAll('.inline-blank'),i=>i.value).join('|')"));
            var reopened=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
            assertEquals(adapter.viewModel().question().selectedOptionIds(),reopened.viewModel().question().selectedOptionIds());
            assertEquals(parent,reopened.viewModel().question().sessionQuestionId());
            stroke(canvas,"final-one",0);submit(engine);waitFor(()->adapter.viewModel().question().result()!=null);
            var first=adapter.viewModel().question().result();assertEquals("INCORRECT",first.status());assertEquals(2,first.score());
            var snapshots=new SqliteAttemptDraftSnapshotRepository(db);var frozen=snapshots.find(first.attemptId()).orElseThrow();
            assertEquals("final-one",frozen.document().strokes().getFirst().id());
            script(engine,"document.querySelector('#practice-retry').click()");waitFor(()->adapter.viewModel().question().state()==SharedPracticeViewModel.State.RETRYING);
            assertTrue(adapter.loadDraft().strokes().isEmpty());assertTrue(adapter.viewModel().question().selectedOptionIds().isEmpty());
            if(matching){assign(engine,"blank_m_2","opt_m_b");waitFor(()->((SharedPracticeViewModel.MatchingPresentation)adapter.viewModel().question().presentation()).assignments().size()==1);}else{choose(engine,"opt_1_a");waitFor(()->adapter.viewModel().question().selectedOptionIds().contains("opt_1_a"));}
            if(matching){for(int n:new int[]{3,5,7,8}){int count=n==3?2:n==5?3:n==7?4:5;assign(engine,"blank_m_"+n,"opt_m_"+(char)('a'+n-1));waitFor(()->((SharedPracticeViewModel.MatchingPresentation)adapter.viewModel().question().presentation()).assignments().size()==count);}}else{choose(engine,"opt_2_c");waitFor(()->adapter.viewModel().question().selectedOptionIds().size()==2);}
            stroke(canvas,"final-two",90);submit(engine);waitFor(()->adapter.viewModel().question().result()!=null);
            var second=adapter.viewModel().question().result();assertEquals("CORRECT",second.status());assertEquals(matching?10:4,second.score());assertEquals("RETRY",second.attemptMode());
            assertEquals(frozen,snapshots.find(first.attemptId()).orElseThrow());assertEquals("final-two",snapshots.find(second.attemptId()).orElseThrow().document().strokes().getFirst().id());
            new SqlitePracticeSessionRepository(db).archive(adapter.snapshot().session().id(),Instant.now());
            var history=new PracticeHistoryService(tx);var detail=history.loadArchivedSessionDetail(bank.assetId(),adapter.snapshot().session().id());
            var projection=new HistoryDraftAdapter(history,bank.assetId(),detail);var row=detail.questions().getFirst();
            var a=projection.load(row,row.attempts().getFirst());var b=projection.load(row,row.attempts().getLast());
            assertNotNull(a.card());assertNotNull(b.card());assertEquals(first.attemptId(),a.card().question().result().attemptId());assertEquals(second.attemptId(),b.card().question().result().attemptId());
            var replay=fx(HistoryDraftWebView::new);var historyStage=fx(()->open(replay.view()));
            try {
                replay.ready().toCompletableFuture().get(30,TimeUnit.SECONDS);
                fx(()->{replay.load(a.card(),a.draft().document());return null;});
                assertEquals("final-one",script(replay.view(),"JSON.parse(window.draftCanvas.getDraft()).strokes[0].id"));
                assertEquals(0,((Number)script(replay.view(),"document.querySelectorAll('#practice-submit,#practice-retry,.composite-item input:not(:disabled),.assignment-slots select:not(:disabled)').length")).intValue());
                script(replay.view(),"document.querySelector('.composite-item input,.assignment-slots select').dispatchEvent(new Event('change',{bubbles:true}))");
                assertEquals(first.attemptId(),script(replay.view(),"window.historyDraftReplay.getViewState().question.result.attemptId"));
                fx(()->{replay.load(b.card(),b.draft().document());return null;});assertEquals("final-two",script(replay.view(),"JSON.parse(window.draftCanvas.getDraft()).strokes[0].id"));
                fx(()->{replay.load(a.card(),a.draft().document());return null;});assertEquals("final-one",script(replay.view(),"JSON.parse(window.draftCanvas.getDraft()).strokes[0].id"));
                assertEquals(frozen,snapshots.find(first.attemptId()).orElseThrow());
            } finally {fx(()->{replay.destroy();replay.destroy();historyStage.close();return null;});}
            script(engine,"window.sharedPractice.destroy();document.querySelector('.composite-item input,.assignment-slots select').dispatchEvent(new Event('change',{bubbles:true}))");
            assertEquals(2,adapter.snapshot().questions().getFirst().attempts().size());
        } finally {fx(()->{canvas.destroy();stage.close();return null;});}
    }
    private static void assign(WebView view,String id,String option) throws Exception {script(view,"(()=>{const i=Array.from(document.querySelectorAll('.assignment-slots select')).find(i=>i.dataset.slotId==='"+id+"');i.value='"+option+"';i.dispatchEvent(new Event('change',{bubbles:true}));})()");}
    private static void choose(WebView view,String id) throws Exception {script(view,"Array.from(document.querySelectorAll('.composite-item input')).find(i=>i.value==='"+id+"').click()");}
    private static void submit(WebView view) throws Exception {script(view,"document.querySelector('#practice-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));document.querySelector('#practice-confirm-submit').click()");}
    private static void stroke(SharedPracticeCanvasWebView canvas,String id,int offset) throws Exception {
        var doc=(ObjectNode)JSON.readTree(fx(canvas::getDraft));
        doc.set("strokes",JSON.readTree("[{\"id\":\""+id+"\",\"tool\":\"PEN\",\"width\":2.4,\"color\":\"#7054a5\",\"points\":[{\"x\":"+(200+offset)+",\"y\":160,\"pressure\":0.5},{\"x\":"+(260+offset)+",\"y\":190,\"pressure\":0.6}]}]"));
        fx(()->{canvas.loadDraft(doc.toString());return null;});
    }
    private static Stage open(WebView view){var stage=new Stage();stage.setScene(new Scene(view,1000,760));stage.setOpacity(0);stage.show();return stage;}
    private static Object script(WebView view,String js) throws Exception {return fx(()->view.getEngine().executeScript(js));}
    private static void waitFor(BooleanSupplier done) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!done.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(30);assertTrue(done.getAsBoolean(),"Core transition completed");}
    private static <T>T fx(Callable<T> action) throws Exception {var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
}
