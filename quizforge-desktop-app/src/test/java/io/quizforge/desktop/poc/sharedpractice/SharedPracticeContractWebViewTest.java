package io.quizforge.desktop.poc.sharedpractice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.desktop.testing.FxTestRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Numeric DOM/SVG evidence for stable logical layout and sequenced authoritative bridge state. */
class SharedPracticeContractWebViewTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;
    @BeforeAll static void startFx() throws Exception { FxTestRuntime.start(); }

    @Test void narrowWidePanAndZoomKeepLogicalWordLinesAndWorldGeometryUnchanged() throws Exception {
        try (var context = example(); var window = open(context.adapter())) {
            var draft = (ObjectNode) JSON.readTree(fx(window.canvas::getDraft));
            assertEquals("1.0", draft.path("schemaVersion").asText());
            assertEquals("1", draft.path("layoutVersion").asText());
            ((ObjectNode) draft.path("questionCard")).put("x", 180).put("y", 95).put("width", 720);
            draft.set("strokes", JSON.readTree("""
                [{"id":"layout-stroke","tool":"PEN","width":2.4,"color":"#7054a5",
                  "points":[{"x":245,"y":155,"pressure":0.5},{"x":360,"y":175,"pressure":0.6}]}]
                """));
            fx(() -> { window.canvas.loadDraft(draft.toString()); return null; });
            // A layout-only long-text probe in the real renderer, without altering Practice data or grading.
            script(window, "document.querySelector('#practice-prompt').textContent = "
                    + "'A fixed logical question card preserves the same words on each line while the viewport changes width. '.repeat(12)");
            var baseline = layout(window);
            assertEquals(720, baseline.path("logicalWidth").asDouble(), 0.01);
            assertTrue(baseline.path("lineCount").asInt() >= 5, "The probe spans several actual DOM text lines");
            assertRelativeStrokeOffset(baseline, 65, 60);

            resize(window, 430);
            var narrow = layout(window);
            assertTrue(narrow.path("viewportWidth").asDouble() < baseline.path("viewportWidth").asDouble());
            assertStableLayout(baseline, narrow);
            assertWorldGeometry(window, draft);

            resize(window, 1180);
            var wide = layout(window);
            assertTrue(wide.path("viewportWidth").asDouble() > baseline.path("viewportWidth").asDouble());
            assertStableLayout(baseline, wide);
            assertWorldGeometry(window, draft);

            var panned = draft.deepCopy();
            ((ObjectNode) panned.path("viewport")).put("x", -83).put("y", 37);
            fx(() -> { window.canvas.loadDraft(panned.toString()); return null; });
            var pan = layout(window);
            assertStableLayout(baseline, pan);
            assertEquals(-83, pan.path("cardScreenX").asDouble() - wide.path("cardScreenX").asDouble(), 0.2);
            assertEquals(37, pan.path("cardScreenY").asDouble() - wide.path("cardScreenY").asDouble(), 0.2);
            assertWorldGeometry(window, draft);

            script(window, "window.draftCanvas.setZoom(0.65, {x:330,y:180})");
            var smallZoom = layout(window);
            assertEquals(0.65, smallZoom.path("zoom").asDouble(), 0.00001);
            assertStableLayout(baseline, smallZoom);
            assertEquals(720 * 0.65, smallZoom.path("screenWidth").asDouble(), 0.2);
            assertWorldGeometry(window, draft);
            script(window, "window.draftCanvas.setZoom(1.6, {x:330,y:180})");
            var largeZoom = layout(window);
            assertEquals(1.6, largeZoom.path("zoom").asDouble(), 0.00001);
            assertStableLayout(baseline, largeZoom);
            assertEquals(720 * 1.6, largeZoom.path("screenWidth").asDouble(), 0.2);
            assertWorldGeometry(window, draft);
            var finalDraft = JSON.readTree(fx(window.canvas::getDraft));
            fx(() -> { window.canvas.loadDraft(finalDraft.toString()); return null; });
            assertEquals(finalDraft, JSON.readTree(fx(window.canvas::getDraft)), "Pan/zoom document round trip preserves both versions");
            System.out.println("DRAFT_LAYOUT_DOM_EVIDENCE=" + JSON.createObjectNode().set("baseline", baseline));
            System.out.println("DRAFT_LAYOUT_RESIZE_ZOOM_EVIDENCE=" + JSON.createObjectNode()
                    .set("narrow", narrow));
            System.out.println("DRAFT_LAYOUT_LARGE_ZOOM_EVIDENCE=" + largeZoom);
        }
    }

    @Test void duplicateEventsAndStaleSuccessOrErrorCannotOverwriteSubmitAndRetry() throws Exception {
        try (var context = example(); var window = open(context.adapter())) {
            var initial = JSON.readTree(fx(window.canvas::practiceJson));
            String correct = initial.path("question").path("options").get(0).path("id").asText();
            var answer = event(initial, "ANSWER_CHANGED", 1);
            answer.putArray("selectedOptionIds").add(correct);
            sendEvent(window, answer);
            var draft = JSON.readTree(fx(window.canvas::practiceJson));
            assertEquals("DRAFT", domState(window));
            var snapshot = fx(window.canvas::snapshot);
            sendEvent(window, answer);
            assertSame(snapshot, fx(window.canvas::snapshot), "Duplicate event does not call Core again");

            var submit = event(initial, "SUBMIT", 2);
            sendEvent(window, submit);
            var submitted = JSON.readTree(fx(window.canvas::practiceJson));
            assertEquals("SUBMITTED", domState(window));
            var attempt = fx(() -> window.canvas.snapshot().questions().getFirst().attempts().getFirst());
            var snapshotAfterSubmit = fx(window.canvas::snapshot);
            sendEvent(window, submit);
            sendEvent(window, answer);
            assertSame(snapshotAfterSubmit, fx(window.canvas::snapshot), "Duplicate submit and old answer cannot re-execute");
            assertEquals(1, fx(() -> window.canvas.snapshot().questions().getFirst().attempts().size()));
            applyResponse(window, response(1, "SUCCESS", draft, null));
            applyResponse(window, response(1, "ERROR", draft, "stale answer error"));
            assertEquals("SUBMITTED", domState(window));
            assertEquals("", script(window, "document.querySelector('#practice-error').textContent"));

            sendEvent(window, event(initial, "RETRY", 3));
            assertEquals("RETRYING", domState(window));
            assertEquals(0, ((Number) script(window,
                    "document.querySelectorAll('input[name=\"practice-answer\"]:checked').length")).intValue());
            applyResponse(window, response(2, "SUCCESS", submitted, null));
            applyResponse(window, response(2, "ERROR", submitted, "stale submit error"));
            applyResponse(window, response(1, "SUCCESS", draft, null));
            assertEquals("RETRYING", domState(window));
            assertEquals("", script(window, "document.querySelector('#practice-error').textContent"));
            assertEquals(attempt, fx(() -> window.canvas.snapshot().questions().getFirst().attempts().getFirst()));
            var ordering = JSON.readTree((String) script(window, "JSON.stringify(window.sharedPractice.getOperationState())"));
            assertEquals(3, ordering.path("lastAppliedSeq").asLong());
            assertEquals(3, ordering.path("lastAuthoritativeSeq").asLong());
        }
    }

    private static JsonNode layout(Window window) throws Exception {
        return JSON.readTree((String) script(window, """
            JSON.stringify((()=>{
              const d=JSON.parse(window.draftCanvas.getDraft()), z=d.viewport.zoom;
              const card=document.querySelector('#question-card'), rect=card.getBoundingClientRect();
              const viewport=document.querySelector('#viewport').getBoundingClientRect();
              const prompt=document.querySelector('#practice-prompt'), text=prompt.firstChild;
              const words=[...text.textContent.matchAll(/\\S+/g)], ranges=[], tops=[];
              for(const word of words){
                const range=document.createRange();range.setStart(text,word.index);range.setEnd(text,word.index+word[0].length);
                const r=range.getBoundingClientRect(), top=(r.top-rect.top)/z;
                let line=tops.findIndex(y=>Math.abs(y-top)<0.2);if(line<0){line=tops.length;tops.push(top);}
                ranges.push({word:word[0],line,x:(r.left-rect.left)/z,y:top,width:r.width/z});
              }
              const stroke=document.querySelector('[data-stroke-id="layout-stroke"]');
              const strokeRect=stroke.getBoundingClientRect(), box=stroke.getBBox();
              // This ascending segment starts at its bounding-box origin. Measure actual rendered
              // bounds: JavaFX WebKit getScreenCTM omits the ancestor CSS scale even though it paints it.
              return {logicalWidth:card.offsetWidth,screenWidth:rect.width,viewportWidth:viewport.width,
                cardScreenX:rect.left-viewport.left,cardScreenY:rect.top-viewport.top,
                zoom:z,lineCount:tops.length,wordRanges:ranges,
                strokeRectWidth:strokeRect.width/z,strokeRectHeight:strokeRect.height/z,
                svgBox:{x:box.x,y:box.y,width:box.width,height:box.height},
                ctm:{a:stroke.getScreenCTM().a,d:stroke.getScreenCTM().d},
                strokeRelativeX:(strokeRect.left-rect.left)/z,strokeRelativeY:(strokeRect.top-rect.top)/z};
            })())
            """));
    }
    private static void assertStableLayout(JsonNode expected, JsonNode actual) {
        assertEquals(expected.path("logicalWidth").asDouble(), actual.path("logicalWidth").asDouble(), 0.01);
        assertEquals(expected.path("lineCount"), actual.path("lineCount"), "Viewport changes cannot reflow logical word lines");
        var a = expected.path("wordRanges"); var b = actual.path("wordRanges");
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).path("word"), b.get(i).path("word"));
            assertEquals(a.get(i).path("line"), b.get(i).path("line"), "Word " + i + " stays on the same logical line");
            for (String field : new String[]{"x", "y", "width"})
                assertEquals(a.get(i).path(field).asDouble(), b.get(i).path(field).asDouble(), 0.2,
                        "Normalized DOM word " + field + " stays fixed through shared World transform");
        }
        assertRelativeStrokeOffset(actual, expected.path("strokeRelativeX").asDouble(), expected.path("strokeRelativeY").asDouble());
        assertEquals(expected.path("svgBox"), actual.path("svgBox"), "Actual SVG local geometry stays fixed");
        for (String field : new String[]{"strokeRectWidth", "strokeRectHeight"})
            assertEquals(expected.path(field).asDouble(), actual.path(field).asDouble(), 0.2,
                    "Actual rendered SVG dimensions follow the same zoom as the card");
    }
    private static void assertRelativeStrokeOffset(JsonNode actual, double x, double y) {
        assertEquals(x, actual.path("strokeRelativeX").asDouble(), 0.2, "SVG and card share the same World x transform");
        assertEquals(y, actual.path("strokeRelativeY").asDouble(), 0.2, "SVG and card share the same World y transform");
    }
    private static void assertWorldGeometry(Window window, JsonNode expected) throws Exception {
        var actual = JSON.readTree(fx(window.canvas::getDraft));
        assertEquals(expected.path("questionCard"), actual.path("questionCard"));
        assertEquals(expected.path("strokes"), actual.path("strokes"));
        assertEquals(expected.path("schemaVersion"), actual.path("schemaVersion"));
        assertEquals(expected.path("layoutVersion"), actual.path("layoutVersion"));
    }
    private static ObjectNode event(JsonNode model, String type, long seq) {
        return JSON.createObjectNode().put("type", type).put("operationSeq", seq)
                .put("sessionId", model.path("session").path("sessionId").asText())
                .put("sessionQuestionId", model.path("question").path("sessionQuestionId").asText());
    }
    private static ObjectNode response(long seq, String status, JsonNode viewModel, String error) {
        var value = JSON.createObjectNode().put("operationSeq", seq).put("status", status);
        value.set("viewModel", viewModel);
        if (error == null) value.putNull("error"); else value.putObject("error").put("message", error);
        return value;
    }
    private static void sendEvent(Window window, JsonNode value) throws Exception { call(window, "window.practiceHost", "onEvent", value.toString()); }
    private static void applyResponse(Window window, JsonNode value) throws Exception { call(window, "window.sharedPractice", "applyResponse", value.toString()); }
    private static void call(Window window, String receiver, String method, String json) throws Exception {
        fx(() -> {
            var engine = window.canvas.view().getEngine();
            var global = (netscape.javascript.JSObject) engine.executeScript("window");
            global.setMember("__testContractJson", json);
            try { engine.executeScript(receiver + "." + method + "(window.__testContractJson)"); }
            finally { global.removeMember("__testContractJson"); }
            return null;
        });
    }
    private static String domState(Window window) throws Exception { return (String) script(window, "document.querySelector('#practice-state').dataset.state"); }
    private static Object script(Window window, String js) throws Exception { return fx(() -> window.canvas.view().getEngine().executeScript(js)); }
    private static void resize(Window window, double width) throws Exception {
        fx(() -> { window.stage.setWidth(width); return null; });
        Thread.sleep(200);
        fx(() -> { window.stage.getScene().getRoot().applyCss(); window.stage.getScene().getRoot().layout(); return null; });
    }
    private SharedPracticeExample.Context example() throws Exception {
        var path = Path.of("examples/step7-practice/Java集合练习.qbank");
        if (!Files.isRegularFile(path)) path = Path.of("../examples/step7-practice/Java集合练习.qbank");
        return SharedPracticeExample.open(path.toAbsolutePath(), temporary.resolve("practice.sqlite"));
    }
    private static Window open(SharedPracticeAdapter adapter) throws Exception {
        var window = fx(() -> {
            var canvas = new SharedPracticeCanvasWebView(adapter);
            var stage = new Stage(); stage.setTitle("Draft contract isolated WebView test");
            stage.setScene(new Scene(canvas.view(), 1000, 760)); stage.setOpacity(0); stage.show();
            return new Window(stage, canvas);
        });
        try { window.canvas.ready().toCompletableFuture().get(30, TimeUnit.SECONDS); }
        catch (Exception failure) { window.close(); throw failure; }
        return window;
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    private record Window(Stage stage, SharedPracticeCanvasWebView canvas) implements AutoCloseable {
        @Override public void close() throws Exception { fx(() -> { canvas.destroy(); stage.close(); return null; }); }
    }
}
