package io.quizforge.desktop.poc.draftcanvas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.desktop.testing.FxTestRuntime;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.Screen;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DraftCanvasWebViewTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @BeforeAll static void startFx() throws Exception { FxTestRuntime.start(); }

    @Test void localPageAndJavaBridgeRoundTripRestoreGeometryAndRejectInvalidDraftAtomically() throws Exception {
        try (var window = open(false)) {
            assertTrue(fx(() -> window.canvas.view().getEngine().getLocation()).startsWith("file:"),
                    "Development tests must load the packaged local page, without Vite");
            var draft = (ObjectNode) JSON.readTree(fx(window.canvas::getDraft));
            assertEquals("1.0", draft.path("schemaVersion").asText());
            ((ObjectNode) draft.path("viewport")).put("x", -47).put("y", 88).put("zoom", 1.25);
            ((ObjectNode) draft.path("questionCard")).put("x", 321).put("y", 91);
            draft.set("strokes", JSON.readTree("""
                [{"id":"bridge-stroke-1","tool":"PEN","width":3,"color":"#7258a8",
                  "points":[{"x":240,"y":100,"pressure":0.5},{"x":275,"y":121,"pressure":0.7}]}]
                """));
            fx(() -> { window.canvas.loadDraft(draft.toString()); return null; });
            var restored = JSON.readTree(fx(window.canvas::getDraft));
            assertEquals(draft.path("viewport"), restored.path("viewport"));
            assertEquals(draft.path("questionCard"), restored.path("questionCard"));
            assertEquals(draft.path("strokes"), restored.path("strokes"));
            var geometry = JSON.readTree(fx(() -> (String) window.canvas.view().getEngine().executeScript("""
                JSON.stringify((()=>{const v=document.querySelector('#viewport').getBoundingClientRect();
                  const c=document.querySelector('#question-card').getBoundingClientRect();
                  return {x:c.left-v.left,y:c.top-v.top,width:c.width};})())
                """)));
            assertEquals((321 - 47) * 1.25, geometry.path("x").asDouble(), 0.1);
            assertEquals((91 + 88) * 1.25, geometry.path("y").asDouble(), 0.1);
            assertEquals(720 * 1.25, geometry.path("width").asDouble(), 0.1,
                    "Card uses the same World offset and zoom as the ink coordinate model");
            fx(() -> { window.canvas.loadDraft(restored.toString()); return null; });
            assertEquals(restored, JSON.readTree(fx(window.canvas::getDraft)));
            try (var fresh = open(false)) {
                fx(() -> { fresh.canvas.loadDraft(restored.toString()); return null; });
                assertEquals(restored, JSON.readTree(fx(fresh.canvas::getDraft)),
                        "A fresh local page restores all serialized geometry and ink");
            }
            var beforeInvalid = fx(window.canvas::getDraft);
            fx(() -> {
                assertThrows(RuntimeException.class, () -> window.canvas.loadDraft("{\"schemaVersion\":\"9\"}"));
                assertThrows(RuntimeException.class, () -> window.canvas.loadDraft("not-json"));
                assertEquals(beforeInvalid, window.canvas.getDraft());
                window.canvas.setMode("PAN");
                return null;
            });
            assertEquals("PAN", JSON.readTree(fx(window.canvas::diagnostics)).path("mode").asText());
        }
    }

    @Test void destroyIsIdempotentAndDisallowsFurtherBridgeUse() throws Exception {
        try (var window = open(false)) {
            fx(() -> {
                window.canvas.destroy();
                assertDoesNotThrow(window.canvas::destroy);
                assertThrows(IllegalStateException.class, window.canvas::getDraft);
                assertThrows(IllegalStateException.class, () -> window.canvas.setMode("PEN"));
                return null;
            });
        }
    }

    @Test void nativeMouseProducesTrustedPointerEventsAndKeepsCardInkAndPanTogether() throws Exception {
        try (var window = open(true)) {
            var robot = fx(Robot::new);
            try {
            Thread.sleep(600); // Visible native window must finish layout before Robot coordinates are used.
            click(robot, fx(() -> new Point2D(window.stage.getX() + 200, window.stage.getY() + 12)));
            nativeEvidence(window, robot, "before-option-B");
            var optionB = point(window, "input[name=answer][value=B]", 0.5, 0.5);
            click(robot, optionB);
            await(() -> Boolean.TRUE.equals(window.canvas.view().getEngine().executeScript(
                    "document.querySelector('input[name=answer][value=B]').checked")), "Native B radio selection");
            nativeEvidence(window, robot, "after-option-B");
            assertEquals(true, fx(() -> window.canvas.view().getEngine().executeScript(
                    "document.querySelector('input[name=answer][value=B]').checked")));
            click(robot, point(window, "#answer-note", 0.3, 0.5));
            fx(() -> { robot.keyType(KeyCode.DIGIT4); robot.keyType(KeyCode.DIGIT2); return null; });
            await(() -> ((String) window.canvas.view().getEngine().executeScript(
                    "document.querySelector('#answer-note').value")).contains("42"), "Native keyboard input");
            assertTrue(((String) fx(() -> window.canvas.view().getEngine().executeScript(
                    "document.querySelector('#answer-note').value"))).contains("42"));
            click(robot, point(window, "#submit-card", 0.5, 0.5));
            await(() -> !((String) window.canvas.view().getEngine().executeScript(
                    "document.querySelector('#card-feedback').textContent")).isBlank(), "Native Submit button");
            assertFalse(((String) fx(() -> window.canvas.view().getEngine().executeScript(
                    "document.querySelector('#card-feedback').textContent"))).isBlank());

            fx(() -> { window.canvas.setMode("PEN"); return null; });
            var overA = point(window, "input[name=answer][value=A]", 0.5, 0.5);
            drag(robot, overA, overA.add(100, 25));
            var withInk = JSON.readTree(fx(window.canvas::getDraft));
            assertEquals(1, withInk.path("strokes").size());
            assertEquals(true, fx(() -> window.canvas.view().getEngine().executeScript(
                    "document.querySelector('input[name=answer][value=B]').checked")),
                    "Pen captured the card surface without changing its chosen answer");
            var firstStroke = withInk.path("strokes").get(0);
            assertTrue(firstStroke.path("points").size() >= 2);
            var blankStart = point(window, "#viewport", 0.85, 0.2);
            drag(robot, blankStart, blankStart.add(30, 45));
            assertEquals(2, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
            click(robot, point(window, "#undo", 0.5, 0.5));
            assertEquals(1, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
            click(robot, point(window, "#redo", 0.5, 0.5));
            assertEquals(2, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
            withInk = JSON.readTree(fx(window.canvas::getDraft));

            fx(() -> { window.canvas.setMode("PAN"); return null; });
            var panStart = point(window, "#viewport", 0.9, 0.9);
            drag(robot, panStart, panStart.add(-63, -39));
            var panned = JSON.readTree(fx(window.canvas::getDraft));
            assertEquals(firstStroke, panned.path("strokes").get(0), "Pan does not rewrite World stroke points");
            assertEquals(withInk.path("questionCard"), panned.path("questionCard"), "Pan does not move card within World");
            assertEquals(-63, panned.path("viewport").path("x").asDouble()
                    - withInk.path("viewport").path("x").asDouble(), 2);
            assertEquals(-39, panned.path("viewport").path("y").asDouble()
                    - withInk.path("viewport").path("y").asDouble(), 2);
            var movedA = point(window, "input[name=answer][value=A]", 0.5, 0.5);
            assertEquals(-63, movedA.getX() - overA.getX(), 2);
            assertEquals(-39, movedA.getY() - overA.getY(), 2);

            fx(() -> { window.canvas.setMode("ERASER"); return null; });
            click(robot, movedA.add(40, 10));
            assertEquals(1, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
            click(robot, point(window, "#undo", 0.5, 0.5));
            assertEquals(2, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
            click(robot, point(window, "#redo", 0.5, 0.5));
            assertEquals(1, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
            click(robot, point(window, "#clear", 0.5, 0.5));
            assertEquals(0, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
            assertEquals(1, ((Number) fx(() -> window.canvas.view().getEngine().executeScript(
                    "document.querySelectorAll('#question-card').length"))).intValue());
            click(robot, point(window, "#undo", 0.5, 0.5));
            assertEquals(1, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
            click(robot, point(window, "#redo", 0.5, 0.5));
            assertEquals(0, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
            var saved = panned.toString();
            fx(() -> { window.canvas.loadDraft(saved); return null; });
            assertEquals(panned, JSON.readTree(fx(window.canvas::getDraft)), "Bridge restores native drawn strokes and panned viewport");
            var diagnostics = JSON.readTree(fx(window.canvas::diagnostics));
            assertTrue(diagnostics.path("supportedPointerEvents").asBoolean());
            var trusted = JSON.createArrayNode();
            for (JsonNode event : diagnostics.path("events")) if (event.path("isTrusted").asBoolean()) trusted.add(event);
            for (String type : new String[]{"pointerdown", "pointermove", "pointerup"}) {
                assertTrue(java.util.stream.StreamSupport.stream(trusted.spliterator(), false)
                        .anyMatch(event -> type.equals(event.path("type").asText())), "Native " + type + " is available");
            }
            assertTrue(java.util.stream.StreamSupport.stream(trusted.spliterator(), false)
                    .anyMatch(event -> "mouse".equals(event.path("pointerType").asText()) && event.has("pressure")));
            System.out.println("DRAFT_CANVAS_NATIVE_POINTER_EVIDENCE=" + diagnostics);
            } catch (Exception | AssertionError failure) {
                nativeEvidence(window, robot, "failure");
                throw failure;
            }
        }
    }

    private static Window open(boolean visible) throws Exception {
        var window = fx(() -> {
            var canvas = new DraftCanvasWebView();
            var stage = new Stage();
            stage.setTitle("Draft Canvas isolated WebView test");
            stage.setScene(new Scene(canvas.view(), 1100, 760));
            if (visible) {
                var bounds = Screen.getPrimary().getVisualBounds();
                stage.setX(bounds.getMinX() + 40);
                stage.setY(bounds.getMinY() + 40);
                stage.setWidth(Math.min(1116, bounds.getWidth() - 80));
                stage.setHeight(Math.min(799, bounds.getHeight() - 80));
                // On a genuinely short desktop, fit the same DOM card without changing the frontend.
                if (bounds.getHeight() < 879) canvas.view().setZoom(0.8);
            }
            if (!visible) stage.setOpacity(0);
            stage.show();
            if (visible) { stage.setAlwaysOnTop(true); stage.toFront(); stage.requestFocus(); }
            return new Window(stage, canvas);
        });
        try { window.canvas.ready().toCompletableFuture().get(30, TimeUnit.SECONDS); }
        catch (Exception failure) { window.close(); throw failure; }
        return window;
    }

    private static Point2D point(Window window, String selector, double fractionX, double fractionY) throws Exception {
        return fx(() -> {
            var engine = window.canvas.view().getEngine();
            var rect = (netscape.javascript.JSObject) engine.executeScript(
                    "document.querySelector('" + selector + "').getBoundingClientRect()");
            double x = ((Number) rect.getMember("left")).doubleValue() + ((Number) rect.getMember("width")).doubleValue() * fractionX;
            double y = ((Number) rect.getMember("top")).doubleValue() + ((Number) rect.getMember("height")).doubleValue() * fractionY;
            // DOM CSS pixels can differ from JavaFX local units after WebView zoom or DPI adaptation.
            double scaleX = window.canvas.view().getWidth() / ((Number) engine.executeScript("window.innerWidth")).doubleValue();
            double scaleY = window.canvas.view().getHeight() / ((Number) engine.executeScript("window.innerHeight")).doubleValue();
            return window.canvas.view().localToScreen(x * scaleX, y * scaleY);
        });
    }
    private static void nativeEvidence(Window window, Robot robot, String phase) throws Exception {
        fx(() -> {
            var web = window.canvas.view();
            var engine = web.getEngine();
            var origin = web.localToScreen(0, 0);
            var geometry = engine.executeScript("JSON.stringify({innerWidth,innerHeight,devicePixelRatio,radio:document.querySelector('input[name=answer][value=B]').getBoundingClientRect(),submit:(()=>{const r=document.querySelector('#submit-card').getBoundingClientRect();return {rect:r,hit:document.elementFromPoint(r.left+r.width/2,r.top+r.height/2)?.id}})(),active:document.activeElement?.id})");
            System.out.println("DRAFT_CANVAS_NATIVE_GEOMETRY " + phase + " mouse=" + robot.getMousePosition()
                    + " view=" + web.getWidth() + "x" + web.getHeight() + " localToScreen=" + origin
                    + " stage=" + window.stage.getX() + "," + window.stage.getY() + "," + window.stage.getWidth()
                    + "x" + window.stage.getHeight() + " focused=" + window.stage.isFocused()
                    + " outputScale=" + window.stage.getOutputScaleX() + "," + window.stage.getOutputScaleY() + " DOM=" + geometry);
            System.out.println("DRAFT_CANVAS_NATIVE_SCREENS " + Screen.getScreens().stream()
                    .map(screen -> "bounds=" + screen.getBounds() + ",visual=" + screen.getVisualBounds()).toList());
            System.out.println("DRAFT_CANVAS_NATIVE_EVENTS " + phase + " " + window.canvas.diagnostics());
            var image = web.snapshot(null, null);
            var directory = Path.of("target/draft-poc");
            Files.createDirectories(directory);
            writeImage(image, directory.resolve("native-" + phase + ".png"));
            // Capture only this POC view's native screen region, never the user's other applications.
            var screenBounds = web.localToScreen(web.getLayoutBounds());
            var screen = robot.getScreenCapture(null, screenBounds.getMinX(), screenBounds.getMinY(),
                    screenBounds.getWidth(), screenBounds.getHeight(), false);
            writeImage(screen, directory.resolve("native-screen-" + phase + ".png"));
            return null;
        });
    }
    private static void writeImage(javafx.scene.image.Image image, Path path) throws Exception {
        var buffered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) for (int x = 0; x < buffered.getWidth(); x++)
            buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        ImageIO.write(buffered, "png", path.toFile());
    }
    private static void await(Callable<Boolean> condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        do {
            if (fx(condition)) return;
            Thread.sleep(80);
        } while (System.nanoTime() < deadline);
        fail("Timed out waiting for " + description);
    }
    private static void click(Robot robot, Point2D point) throws Exception {
        fx(() -> { robot.mouseMove(point); return null; });
        Thread.sleep(100);
        fx(() -> { robot.mousePress(MouseButton.PRIMARY); return null; });
        Thread.sleep(80);
        fx(() -> { robot.mouseRelease(MouseButton.PRIMARY); return null; });
        Thread.sleep(160);
    }
    private static void drag(Robot robot, Point2D from, Point2D to) throws Exception {
        fx(() -> { robot.mouseMove(from); return null; });
        Thread.sleep(100);
        fx(() -> { robot.mousePress(MouseButton.PRIMARY); return null; });
        Thread.sleep(100);
        for (int step = 1; step <= 6; step++) {
            double fraction = step / 6.0;
            var position = from.add(to.subtract(from).multiply(fraction));
            fx(() -> { robot.mouseMove(position); return null; });
            Thread.sleep(50);
        }
        fx(() -> { robot.mouseRelease(MouseButton.PRIMARY); return null; });
        Thread.sleep(200);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
    private record Window(Stage stage, DraftCanvasWebView canvas) implements AutoCloseable {
        @Override public void close() throws Exception {
            fx(() -> { canvas.destroy(); stage.close(); return null; });
        }
    }
}
