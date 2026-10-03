package io.quizforge.desktop.poc.sharedpractice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.port.PracticeTransaction;
import io.quizforge.core.practice.PracticeSessionService;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.desktop.testing.FxTestRuntime;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual example qbank -> existing Core service -> narrow bridge -> local DOM card. */
class SharedPracticeCanvasWebViewTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;
    @BeforeAll static void startFx() throws Exception { FxTestRuntime.start(); }

    @Test void localPageLoadsRealQuestionAndAuthoritativeDraftSubmitRetryRoundTrips() throws Exception {
        try (var context = example(); var window = open(context.adapter(), false)) {
            assertTrue(fx(() -> window.canvas.view().getEngine().getLocation()).startsWith("file:"));
            var initial = state(window);
            assertEquals("UNANSWERED", initial.path("question").path("state").asText());
            assertEquals("UNANSWERED", domState(window));
            assertEquals("SINGLE_CHOICE", initial.path("question").path("type").asText());
            assertFalse(initial.path("question").path("prompt").path("text").asText().isBlank());
            assertEquals(initial.path("question").path("options").size(), ((Number) script(window,
                    "document.querySelectorAll('input[name=\"practice-answer\"]').length")).intValue());
            assertEquals(0, selectedCount(window));
            assertTrue(initial.path("question").path("result").isNull());
            String option = initial.path("question").path("options").get(0).path("id").asText();
            event(window, "ANSWER_CHANGED", option);
            assertEquals("DRAFT", domState(window));
            assertEquals(option, state(window).path("question").path("selectedOptionIds").get(0).asText());
            assertEquals(option, script(window,
                    "document.querySelector('input[name=\"practice-answer\"]:checked').value"));
            event(window, "SUBMIT", null);
            var submitted = state(window).path("question");
            assertEquals("SUBMITTED", domState(window));
            assertEquals("CORRECT", submitted.path("result").path("status").asText());
            assertEquals("INITIAL", submitted.path("result").path("attemptMode").asText());
            assertFalse(((String) script(window, "document.querySelector('#practice-result').textContent")).isBlank());
            var initialAttempt = fx(() -> window.canvas.snapshot().questions().getFirst().attempts().getFirst());
            event(window, "RETRY", null);
            assertEquals("RETRYING", domState(window));
            assertEquals(0, selectedCount(window));
            assertEquals(0, state(window).path("question").path("selectedOptionIds").size());
            event(window, "ANSWER_CHANGED", option);
            assertEquals("RETRYING", domState(window));
            event(window, "SUBMIT", null);
            var attempts = fx(() -> window.canvas.snapshot().questions().getFirst().attempts());
            assertEquals(2, attempts.size());
            assertEquals(initialAttempt, attempts.getFirst(), "Old INITIAL attempt remains immutable");
            assertEquals(QuestionAttempt.Mode.RETRY, attempts.getLast().attemptMode());
            assertEquals("SUBMITTED", domState(window));
            assertEquals("RETRY", state(window).path("question").path("result").path("attemptMode").asText());
        }
    }

    @Test void invalidSelectionAndStaleEventRestoreOriginalStateWithoutFakeSuccess() throws Exception {
        try (var context = example(); var window = open(context.adapter(), false)) {
            var initial = state(window);
            String option = initial.path("question").path("options").get(0).path("id").asText();
            event(window, "ANSWER_CHANGED", option);
            var before = fx(window.canvas::snapshot);
            var beforeJson = state(window);
            // Deliberately emulate the browser's native checked change before a failing semantic event.
            script(window, "document.querySelectorAll('input[name=\"practice-answer\"]')[1].checked=true");
            event(window, "ANSWER_CHANGED", "option-does-not-exist");
            assertEquals(before, fx(window.canvas::snapshot));
            assertEquals(beforeJson, state(window));
            assertEquals("DRAFT", domState(window));
            assertEquals(option, script(window,
                    "document.querySelector('input[name=\"practice-answer\"]:checked').value"));
            assertFalse(((String) script(window, "document.querySelector('#practice-error').textContent")).isBlank());
            assertEquals(0, fx(() -> window.canvas.snapshot().questions().getFirst().attempts().size()));
            var stale = JSON.createObjectNode().put("type", "SUBMIT").put("sessionId", "stale-session")
                    .put("sessionQuestionId", beforeJson.path("question").path("sessionQuestionId").asText())
                    .put("operationSeq", nextOperationSeq(window));
            sendEvent(window, stale.toString());
            assertEquals(before, fx(window.canvas::snapshot));
            assertEquals("DRAFT", domState(window));
            assertTrue(state(window).path("question").path("result").isNull());
            event(window, "SUBMIT", null);
            assertEquals("SUBMITTED", domState(window), "User may retry after a non-destructive error");
            assertEquals("", script(window, "document.querySelector('#practice-error').textContent"));
            fx(() -> { window.canvas.destroy(); window.canvas.destroy();
                assertThrows(IllegalStateException.class, window.canvas::getDraft); return null; });
        }
    }

    @Test void coreSubmitFailureAfterWritesRollsBackAndNeverDisplaysSubmittedSuccess() throws Exception {
        try (var context = example()) {
            var option = context.adapter().viewModel().question().options().getFirst().id();
            context.adapter().answerChanged(Set.of(option));
            var before = context.adapter().snapshot();
            var transaction = new SqlitePracticeTransaction(new SqliteDatabase(temporary.resolve("practice.sqlite")));
            var failingTransaction = new PracticeTransaction() {
                @Override public <T> T execute(Function<Repositories, T> operation) {
                    return transaction.execute(repositories -> {
                        var result = operation.apply(repositories);
                        if (result instanceof java.util.Optional<?>) return result; // Allow initial Draft read.
                        throw new IllegalStateException("Injected failure before transaction commit");
                    });
                }
            };
            var failingAdapter = new SharedPracticeAdapter(new PracticeSessionService(failingTransaction, Clock.systemUTC()), before);
            try (var window = open(failingAdapter, false)) {
                event(window, "SUBMIT", null);
                assertEquals(before, fx(window.canvas::snapshot));
                assertEquals("DRAFT", domState(window));
                assertEquals(1, selectedCount(window));
                assertTrue(state(window).path("question").path("result").isNull());
                assertTrue(((String) script(window, "document.querySelector('#practice-result').textContent")).isBlank());
                assertTrue(((String) script(window, "document.querySelector('#practice-error').textContent"))
                        .contains("Injected failure before transaction commit"));
                var ordering = JSON.readTree((String) script(window, "JSON.stringify(window.sharedPractice.getOperationState())"));
                assertEquals(1, ordering.path("lastAppliedSeq").asLong(), "Failed response advances stale-response watermark");
                assertEquals(0, ordering.path("lastAuthoritativeSeq").asLong(), "Core failure cannot advance authoritative state");
                int storedAttempts = transaction.execute(repositories -> repositories.attempts()
                        .listBySessionQuestion(before.questions().getFirst().sessionQuestion().id()).size());
                assertEquals(0, storedAttempts);
                assertEquals(before.questions().getFirst().sessionQuestion(), transaction.execute(repositories ->
                        repositories.questions().findBySessionIdAndQuestionId(before.session().id(),
                                before.questions().getFirst().sessionQuestion().questionId()).orElseThrow()));
            }
        }
    }

    @Test void nativeMouseAnswersCoreWhilePenPanAndRetryPreserveWorldAndImmutableAttempts() throws Exception {
        try (var context = example(); var window = open(context.adapter(), true)) {
            var robot = fx(Robot::new);
            Thread.sleep(600);
            click(robot, fx(() -> new Point2D(window.stage.getX() + 200, window.stage.getY() + 12)));
            try {
                var initial = state(window);
                var options = initial.path("question").path("options");
                String correct = options.get(0).path("id").asText();
                String incorrect = options.get(1).path("id").asText();
                var correctSelector = "input[name=practice-answer][value=" + correct + "]";
                var incorrectSelector = "input[name=practice-answer][value=" + incorrect + "]";
                click(robot, point(window, incorrectSelector, 0.5, 0.5));
                await(() -> "DRAFT".equals(window.canvas.view().getEngine().executeScript(
                        "document.querySelector('#practice-state').dataset.state")), "Native answer selection reaches Core DRAFT");
                assertEquals(incorrect, state(window).path("question").path("selectedOptionIds").get(0).asText());

                fx(() -> { window.canvas.setMode("PEN"); return null; });
                var overRadio = point(window, correctSelector, 0.5, 0.5);
                var beforePen = fx(window.canvas::snapshot);
                drag(robot, overRadio, overRadio.add(110, 20));
                var withInk = JSON.readTree(fx(window.canvas::getDraft));
                assertEquals(1, withInk.path("strokes").size());
                assertEquals(beforePen, fx(window.canvas::snapshot), "PEN does not send answer or submit events");
                assertEquals(incorrect, script(window,
                        "document.querySelector('input[name=\"practice-answer\"]:checked').value"));
                assertTrue(withInk.path("strokes").get(0).path("points").size() >= 2);

                fx(() -> { window.canvas.setMode("ERASER"); return null; });
                click(robot, overRadio.add(40, 7));
                assertEquals(0, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
                assertEquals(beforePen, fx(window.canvas::snapshot), "ERASER only changes ink");
                click(robot, point(window, "#undo", 0.5, 0.5));
                assertEquals(1, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size());
                withInk = JSON.readTree(fx(window.canvas::getDraft));

                fx(() -> { window.canvas.setMode("PAN"); return null; });
                var panStart = point(window, "#viewport", 0.9, 0.9);
                drag(robot, panStart, panStart.add(-63, -39));
                var panned = JSON.readTree(fx(window.canvas::getDraft));
                assertEquals(withInk.path("strokes"), panned.path("strokes"), "PAN retains World stroke coordinates");
                assertEquals(withInk.path("questionCard"), panned.path("questionCard"));
                var movedRadio = point(window, correctSelector, 0.5, 0.5);
                assertEquals(-63, movedRadio.getX() - overRadio.getX(), 2);
                assertEquals(-39, movedRadio.getY() - overRadio.getY(), 2);
                assertEquals(beforePen, fx(window.canvas::snapshot));
                fx(() -> { window.canvas.setMode("INTERACT"); return null; });
                click(robot, point(window, "#practice-submit", 0.5, 0.5));
                assertEquals(0, fx(() -> window.canvas.snapshot().questions().getFirst().attempts().size()),
                        "Submit confirmation does not itself create an attempt");
                click(robot, point(window, "#practice-confirm-submit", 0.5, 0.5));
                await(() -> "SUBMITTED".equals(window.canvas.view().getEngine().executeScript(
                        "document.querySelector('#practice-state').dataset.state")), "Native Submit reaches Core SUBMITTED");
                var result = state(window).path("question").path("result");
                assertEquals("INCORRECT", result.path("status").asText());
                assertEquals(0, result.path("score").asDouble());
                var initialAttempt = fx(() -> window.canvas.snapshot().questions().getFirst().attempts().getFirst());
                assertEquals(QuestionAttempt.Mode.INITIAL, initialAttempt.attemptMode());
                click(robot, point(window, "#practice-retry", 0.5, 0.5));
                await(() -> "RETRYING".equals(window.canvas.view().getEngine().executeScript(
                        "document.querySelector('#practice-state').dataset.state")), "Native Retry reaches Core RETRYING");
                assertEquals(0, selectedCount(window));
                assertEquals(0, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size(), "Retry starts with empty ink");
                assertEquals(0, JSON.readTree(fx(window.canvas::getDraft)).path("viewport").path("x").asDouble(), "Retry starts with default viewport");
                click(robot, point(window, correctSelector, 0.5, 0.5));
                assertEquals("RETRYING", domState(window));
                click(robot, point(window, "#practice-submit", 0.5, 0.5));
                click(robot, point(window, "#practice-confirm-submit", 0.5, 0.5));
                await(() -> "SUBMITTED".equals(window.canvas.view().getEngine().executeScript(
                        "document.querySelector('#practice-state').dataset.state")), "Native RETRY submission");
                var attempts = fx(() -> window.canvas.snapshot().questions().getFirst().attempts());
                assertEquals(2, attempts.size());
                assertEquals(initialAttempt, attempts.getFirst());
                assertEquals(QuestionAttempt.Mode.RETRY, attempts.getLast().attemptMode());
                assertEquals("CORRECT", state(window).path("question").path("result").path("status").asText());
                assertEquals(0, JSON.readTree(fx(window.canvas::getDraft)).path("strokes").size(), "Submitted canvas is cleared after freeze");
                var diagnostics = JSON.readTree(fx(window.canvas::diagnostics));
                for (String type : Set.of("pointerdown", "pointermove", "pointerup")) {
                    assertTrue(java.util.stream.StreamSupport.stream(diagnostics.path("events").spliterator(), false)
                            .anyMatch(event -> type.equals(event.path("type").asText()) && event.path("isTrusted").asBoolean()),
                            "WebView emits native trusted " + type);
                }
                System.out.println("SHARED_PRACTICE_NATIVE_POINTER_EVIDENCE=" + diagnostics);
                System.out.println("SHARED_PRACTICE_NATIVE_FINAL_VIEW=" + fx(window.canvas::practiceJson));
                System.out.println("SHARED_PRACTICE_NATIVE_ATTEMPTS=" + attempts);
                capture(window, "native-final");
            } catch (Exception | AssertionError failure) {
                System.out.println("SHARED_PRACTICE_NATIVE_FAILURE_STATE=" + fx(window.canvas::practiceJson));
                System.out.println("SHARED_PRACTICE_NATIVE_FAILURE_EVENTS=" + fx(window.canvas::diagnostics));
                capture(window, "native-failure");
                throw failure;
            }
        }
    }

    private SharedPracticeExample.Context example() throws Exception {
        var path = Path.of("examples/step7-practice/Java集合练习.qbank");
        if (!Files.isRegularFile(path)) path = Path.of("../examples/step7-practice/Java集合练习.qbank");
        return SharedPracticeExample.open(path.toAbsolutePath(), temporary.resolve("practice.sqlite"));
    }
    private static JsonNode state(Window window) throws Exception { return JSON.readTree(fx(window.canvas::practiceJson)); }
    private static String domState(Window window) throws Exception {
        return (String) script(window, "document.querySelector('#practice-state').dataset.state");
    }
    private static int selectedCount(Window window) throws Exception {
        return ((Number) script(window, "document.querySelectorAll('input[name=\"practice-answer\"]:checked').length")).intValue();
    }
    private static Object script(Window window, String js) throws Exception {
        return fx(() -> window.canvas.view().getEngine().executeScript(js));
    }
    private static void event(Window window, String type, String option) throws Exception {
        var current = state(window);
        var envelope = JSON.createObjectNode().put("type", type)
                .put("sessionId", current.path("session").path("sessionId").asText())
                .put("sessionQuestionId", current.path("question").path("sessionQuestionId").asText())
                .put("operationSeq", nextOperationSeq(window));
        if (option != null) envelope.putArray("selectedOptionIds").add(option);
        sendEvent(window, envelope.toString());
    }
    private static long nextOperationSeq(Window window) throws Exception {
        return ((Number) script(window, "window.sharedPractice.getOperationState().lastAppliedSeq")).longValue() + 1;
    }
    private static void sendEvent(Window window, String json) throws Exception {
        fx(() -> {
            var engine = window.canvas.view().getEngine();
            var global = (netscape.javascript.JSObject) engine.executeScript("window");
            // JavaFX unwraps a Java bridge object on read; call through JS instead of casting it.
            global.setMember("__testPracticeEvent", json);
            try { engine.executeScript("window.practiceHost.onEvent(window.__testPracticeEvent)"); }
            finally { global.removeMember("__testPracticeEvent"); }
            return null;
        });
    }
    private static Window open(SharedPracticeAdapter adapter, boolean visible) throws Exception {
        var window = fx(() -> {
            var canvas = new SharedPracticeCanvasWebView(adapter);
            var stage = new Stage();
            stage.setTitle("Shared Practice isolated WebView test");
            stage.setScene(new Scene(canvas.view(), 1100, 760));
            if (visible) {
                var bounds = Screen.getPrimary().getVisualBounds();
                stage.setX(bounds.getMinX() + 40);
                stage.setY(bounds.getMinY() + 40);
                stage.setWidth(Math.min(1116, bounds.getWidth() - 80));
                stage.setHeight(Math.min(799, bounds.getHeight() - 80));
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
            double scaleX = window.canvas.view().getWidth() / ((Number) engine.executeScript("window.innerWidth")).doubleValue();
            double scaleY = window.canvas.view().getHeight() / ((Number) engine.executeScript("window.innerHeight")).doubleValue();
            return window.canvas.view().localToScreen(x * scaleX, y * scaleY);
        });
    }
    private static void await(Callable<Boolean> condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        do { if (fx(condition)) return; Thread.sleep(80); } while (System.nanoTime() < deadline);
        fail("Timed out waiting for " + description);
    }
    private static void capture(Window window, String name) throws Exception {
        fx(() -> {
            // Only this POC's WebView pixels, never the user's other applications.
            var image = window.canvas.view().snapshot(null, null);
            var buffered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < buffered.getHeight(); y++) for (int x = 0; x < buffered.getWidth(); x++)
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            var directory = Path.of("target/shared-practice-poc");
            Files.createDirectories(directory);
            ImageIO.write(buffered, "png", directory.resolve(name + ".png").toFile());
            return null;
        });
    }
    private static void click(Robot robot, Point2D point) throws Exception {
        fx(() -> { robot.mouseMove(point); return null; }); Thread.sleep(100);
        fx(() -> { robot.mousePress(MouseButton.PRIMARY); return null; }); Thread.sleep(80);
        fx(() -> { robot.mouseRelease(MouseButton.PRIMARY); return null; }); Thread.sleep(160);
    }
    private static void drag(Robot robot, Point2D from, Point2D to) throws Exception {
        fx(() -> { robot.mouseMove(from); return null; }); Thread.sleep(100);
        fx(() -> { robot.mousePress(MouseButton.PRIMARY); return null; }); Thread.sleep(100);
        for (int step = 1; step <= 6; step++) {
            var position = from.add(to.subtract(from).multiply(step / 6.0));
            fx(() -> { robot.mouseMove(position); return null; }); Thread.sleep(50);
        }
        fx(() -> { robot.mouseRelease(MouseButton.PRIMARY); return null; }); Thread.sleep(200);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
    private record Window(Stage stage, SharedPracticeCanvasWebView canvas) implements AutoCloseable {
        @Override public void close() throws Exception {
            fx(() -> { canvas.destroy(); stage.close(); return null; });
        }
    }
}
