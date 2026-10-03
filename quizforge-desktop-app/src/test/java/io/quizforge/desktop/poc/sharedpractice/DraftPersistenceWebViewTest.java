package io.quizforge.desktop.poc.sharedpractice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.desktop.testing.FxTestRuntime;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.DraftCanvasJsonCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual local JS/WebKit/Core/SQLite lifecycle; no localStorage or service mock. */
class DraftPersistenceWebViewTest {
    @TempDir Path temp;
    static final ObjectMapper JSON = new ObjectMapper();
    static final DraftCanvasJsonCodec CODEC = new DraftCanvasJsonCodec();
    @BeforeAll static void init() throws Exception { FxTestRuntime.start(); }
    static DraftCanvasDocument ink(String id) {
        return new DraftCanvasDocument("1.0", "1", new DraftCanvasDocument.Viewport(-31, 47, 1.25),
                new DraftCanvasDocument.QuestionCard(140, 95, 720), List.of(new DraftCanvasDocument.Stroke(id, "PEN", "#7054a5", 2.4,
                    List.of(new DraftCanvasDocument.Point(190, 140, .5), new DraftCanvasDocument.Point(310, 180, .8)))));
    }
    @Test void debouncePersistsAndFreshWebViewAndServiceRestoreTheSameActiveDraft() throws Exception {
        try(var first = context()) {
            try(var window = open(first.adapter())) {
                fx(() -> { window.canvas.loadDraft(CODEC.encode(ink("autosaved"))); return null; });
                await(() -> !Boolean.TRUE.equals(script(window,"window.sharedPractice.autosaveState().dirty")));
                assertEquals(ink("autosaved"), first.adapter().loadDraft());
                assertEquals(1, ((Number)script(window,"window.sharedPractice.diagnostics().sent")).intValue(), "One debounce save, no pointermove writes");
            }
        }
        try(var reopened=context();var window=open(reopened.adapter())) {
            assertEquals(ink("autosaved"),DraftCanvasJsonCodec.decode(fx(window.canvas::getDraft)));
            assertFalse((Boolean)script(window,"window.sharedPractice.autosaveState().dirty"));
        }
    }
    @Test void immediateFinalStrokeSubmitFlushesThenRetryAndSecondSubmitRetainIndependentSnapshots() throws Exception {
        try(var context=context();var window=open(context.adapter())) {
            choose(window);
            fx(() -> { window.canvas.loadDraft(CODEC.encode(ink("last-stroke-A"))); return null; });
            assertTrue((Boolean)script(window,"window.sharedPractice.autosaveState().dirty"));
            confirmSubmit(window); await(() -> "SUBMITTED".equals(script(window,"window.sharedPractice.getViewState().question.state")));
            var first=context.snapshot().questions().getFirst().attempts().getFirst();
            var firstSnapshot=context.service().findAttemptDraftSnapshot(first.id()).orElseThrow();
            assertEquals(ink("last-stroke-A"),firstSnapshot.document());
            assertTrue(loadActive(context).isEmpty());
            assertTrue(DraftCanvasJsonCodec.decode(fx(window.canvas::getDraft)).strokes().isEmpty());
            script(window,"document.querySelector('#practice-retry').click()");
            await(() -> "RETRYING".equals(script(window,"window.sharedPractice.getViewState().question.state")));
            assertEquals(DraftCanvasDocument.createEmpty(),DraftCanvasJsonCodec.decode(fx(window.canvas::getDraft)));
            assertTrue(loadActive(context).isEmpty()); choose(window);
            fx(() -> { window.canvas.loadDraft(CODEC.encode(ink("last-stroke-B"))); return null; });
            confirmSubmit(window); await(() -> "SUBMITTED".equals(script(window,"window.sharedPractice.getViewState().question.state")));
            var second=context.snapshot().questions().getFirst().attempts().getLast();
            assertNotEquals(first.id(),second.id()); assertEquals(firstSnapshot,context.service().findAttemptDraftSnapshot(first.id()).orElseThrow());
            assertEquals(ink("last-stroke-B"),context.service().findAttemptDraftSnapshot(second.id()).orElseThrow().document());
            assertEquals(first,context.snapshot().questions().getFirst().attempts().getFirst());
        }
    }
    @Test void failedDraftSavePreservesInkAndStopsSubmitUntilAnAcknowledgedSaveSucceeds() throws Exception {
        try(var context=context();var window=open(context.adapter())) {
            choose(window);
            sql("CREATE TRIGGER injected_save BEFORE INSERT ON practice_draft_canvas BEGIN SELECT RAISE(ABORT, 'draft save failure'); END");
            fx(() -> { window.canvas.loadDraft(CODEC.encode(ink("unsaved-final"))); return null; });
            confirmSubmit(window);
            await(() -> ((String)script(window,"document.querySelector('#practice-error').textContent")).contains("persist active"));
            assertEquals("DRAFT",script(window,"window.sharedPractice.getViewState().question.state"));
            assertTrue(context.snapshot().questions().getFirst().attempts().isEmpty()); assertTrue(loadActive(context).isEmpty());
            assertEquals(ink("unsaved-final"),DraftCanvasJsonCodec.decode(fx(window.canvas::getDraft)));
            assertTrue((Boolean)script(window,"window.sharedPractice.autosaveState().dirty"));
            var state=JSON.readTree((String)script(window,"JSON.stringify(window.sharedPractice.getOperationState())"));
            assertEquals(2,state.path("lastAppliedSeq").asLong()); assertEquals(1,state.path("lastAuthoritativeSeq").asLong());
            sql("DROP TRIGGER injected_save"); confirmSubmit(window);
            await(() -> "SUBMITTED".equals(script(window,"window.sharedPractice.getViewState().question.state")));
            var attempt=context.snapshot().questions().getFirst().attempts().getFirst();
            assertEquals(ink("unsaved-final"),context.service().findAttemptDraftSnapshot(attempt.id()).orElseThrow().document());
        }
    }
    private java.util.Optional<io.quizforge.core.practice.draft.ActiveDraftCanvas> loadActive(SharedPracticeExample.Context c) {
        var s=c.snapshot().session();return c.service().loadActiveDraftCanvas(s.id(),s.questionBankContentId(),s.currentQuestionId());
    }
    private void sql(String sql) throws Exception { try(var c=new SqliteDatabase(temp.resolve("practice.db")).openConnection();var st=c.createStatement()){st.execute(sql);} }
    private static void choose(Window w) throws Exception {
        script(w,"var input=document.querySelector('input[name=practice-answer]'); input.checked=true; input.dispatchEvent(new Event('change',{bubbles:true}));");
        await(() -> !Boolean.TRUE.equals(script(w,"document.querySelector('#practice-submit').disabled")));
    }
    private static void confirmSubmit(Window w) throws Exception {
        script(w,"document.querySelector('#practice-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})); document.querySelector('#practice-confirm-submit').click();");
    }
    private SharedPracticeExample.Context context() {
        var p=Path.of("examples/step7-practice/Java集合练习.qbank"); if(!Files.isRegularFile(p))p=Path.of("../examples/step7-practice/Java集合练习.qbank");
        return SharedPracticeExample.open(p.toAbsolutePath(),temp.resolve("practice.db"));
    }
    private static Window open(SharedPracticeAdapter adapter) throws Exception {
        // Attach WebKit to an initialized native surface, as the formal FilePane does.
        var stage=fx(() -> {var s=new Stage();s.setScene(new Scene(new javafx.scene.layout.StackPane(),1100,760));s.setOpacity(0);s.show();return s;});
        var w=fx(() -> {var c=new SharedPracticeCanvasWebView(adapter);stage.getScene().setRoot(c.view());return new Window(stage,c);});
        try { w.canvas.ready().toCompletableFuture().get(30,TimeUnit.SECONDS); return w; }
        catch (Exception failure) { w.close(); throw failure; }
    }
    private static Object script(Window w,String code) throws Exception{return fx(() -> w.canvas.view().getEngine().executeScript(code));}
    private static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void await(Callable<Boolean> condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<end){if(condition.call())return;Thread.sleep(50);}fail("Timed out waiting for persisted bridge state");}
    private record Window(Stage stage,SharedPracticeCanvasWebView canvas)implements AutoCloseable{
        public void close()throws Exception{fx(() -> {canvas.destroy();stage.close();return null;});}
    }
}
