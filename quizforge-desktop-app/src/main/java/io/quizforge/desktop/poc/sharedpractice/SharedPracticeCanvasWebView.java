package io.quizforge.desktop.poc.sharedpractice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.practice.ActivePracticeSnapshot;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** Isolated local live-card host. The adapter alone updates authoritative Practice state. */
public final class SharedPracticeCanvasWebView {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final WebView view = new WebView();
    private final SharedPracticeAdapter adapter;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    // WebKit holds a weak reference to Java objects; retain the narrow bridge for this page's lifetime.
    private final PracticeHost host = new PracticeHost();
    private final PracticeMutationQueue mutations = new PracticeMutationQueue();
    private JSObject practice;
    private JSObject canvas;
    private boolean destroyed;
    private boolean practiceLoaded;

    public SharedPracticeCanvasWebView(SharedPracticeAdapter adapter) {
        requireFxThread();
        this.adapter = Objects.requireNonNull(adapter);
        var page = Objects.requireNonNull(getClass().getResource("/editor/draft-canvas/shared-practice.html"),
                "Build the independent draft-canvas frontend before launching Shared Practice");
        view.setContextMenuEnabled(false);
        view.getEngine().getLoadWorker().stateProperty().addListener((observable, before, after) -> {
            if (destroyed) return;
            if (after == Worker.State.SUCCEEDED) {
                try {
                    var window = (JSObject) view.getEngine().executeScript("window");
                    practice = (JSObject) window.getMember("sharedPractice");
                    canvas = (JSObject) window.getMember("draftCanvas");
                    if (practice == null || canvas == null) throw new IllegalStateException("Local page bridges are missing");
                    window.setMember("practiceHost", host);
                    practice.call("bindHost");
                } catch (RuntimeException failure) { ready.completeExceptionally(failure); }
            } else if (after == Worker.State.FAILED || after == Worker.State.CANCELLED) {
                ready.completeExceptionally(new IllegalStateException("Local Shared Practice page failed to load",
                        view.getEngine().getLoadWorker().getException()));
            }
        });
        view.getEngine().load(page.toExternalForm());
    }

    public WebView view() { return view; }
    public CompletionStage<Void> ready() { return ready.minimalCompletionStage(); }
    public ActivePracticeSnapshot snapshot() { requireFxThread(); return adapter.snapshot(); }
    public String practiceJson() { requireFxThread(); return adapter.viewModelJson(); }
    public String getDraft() { activeCanvas(); return (String) callPage("draftCanvas", "getDraft", null); }
    public void loadDraft(String json) { activeCanvas(); callPage("draftCanvas", "loadDraft", Objects.requireNonNull(json)); }
    public void setMode(String mode) { activeCanvas(); callPage("draftCanvas", "setMode", Objects.requireNonNull(mode)); }
    public String diagnostics() {
        activeCanvas();
        return (String) view.getEngine().executeScript("JSON.stringify(window.draftCanvas.diagnostics())");
    }

    public void destroy() {
        requireFxThread();
        if (destroyed) return;
        destroyed = true;
        mutations.clear();
        try {
            // Resolve through the live page globals: a retained JSObject method reference can
            // become invalid after WebKit GC during a native snapshot/long-running interaction.
            if (practice != null || canvas != null) view.getEngine().executeScript(
                    "if(window.sharedPractice) window.sharedPractice.destroy();"
                    + "if(window.draftCanvas) window.draftCanvas.destroy();");
        } finally {
            practice = null;
            canvas = null;
            ready.completeExceptionally(new IllegalStateException("Shared Practice page was destroyed before readiness"));
            view.getEngine().load(null);
        }
    }

    /** This is the complete JS -> Java business surface: ready plus a semantic event envelope. */
    public final class PracticeHost {
        public void ready() {
            requireFxThread();
            if (destroyed || practiceLoaded) return;
            try {
                callPage("sharedPractice", "loadPractice", adapter.viewModelJson());
                practiceLoaded = true;
                ready.complete(null);
            } catch (RuntimeException failure) { ready.completeExceptionally(failure); }
        }

        public void onEvent(String json) {
            requireFxThread();
            if (destroyed || !practiceLoaded) return;
            try {
                JsonNode event = JSON.readTree(Objects.requireNonNull(json));
                if (event == null || !event.isObject()) return;
                var sequence = event.path("operationSeq");
                if (!sequence.isIntegralNumber() || !sequence.canConvertToLong()) return;
                long operationSeq = sequence.asLong();
                // One queue for this session/question. SUBMIT and RETRY are FIFO barriers, without coalescing.
                mutations.offer(operationSeq, () -> processEvent(operationSeq, event));
            } catch (JsonProcessingException | NullPointerException malformed) {
                // There is no legal sequence to echo. Malformed envelopes cannot mutate Core or UI state.
            }
        }

        private void processEvent(long operationSeq, JsonNode event) {
            if (destroyed) return;
            // Read at execution time, after every earlier FIFO mutation, rather than at enqueue time.
            var before = adapter.viewModelJson();
            String status;
            String viewModel;
            String message = null;
            try {
                JsonNode current = JSON.readTree(before);
                requireIdentity(event, "sessionId", current.path("session").path("sessionId"));
                requireIdentity(event, "sessionQuestionId", current.path("question").path("sessionQuestionId"));
                switch (event.path("type").asText()) {
                    case "ANSWER_CHANGED" -> {
                        var ids = event.path("selectedOptionIds");
                        if (!ids.isArray()) throw new IllegalArgumentException("selectedOptionIds must be an array");
                        var selected = new LinkedHashSet<String>();
                        for (var id : ids) {
                            if (!id.isTextual() || id.asText().isBlank())
                                throw new IllegalArgumentException("Option IDs must be nonblank strings");
                            selected.add(id.asText());
                        }
                        adapter.answerChanged(selected);
                    }
                    case "SUBMIT" -> adapter.submit();
                    case "RETRY" -> adapter.retry();
                    default -> throw new IllegalArgumentException("Unsupported Practice event");
                }
                status = "SUCCESS";
                viewModel = adapter.viewModelJson();
            } catch (JsonProcessingException | RuntimeException failure) {
                status = "ERROR";
                viewModel = before;
                message = "操作失败，请重试：" + failure.getMessage();
            }
            // A single response envelope lets JS reject stale success AND stale error atomically.
            callPage("sharedPractice", "applyResponse", response(operationSeq, status, viewModel, message));
        }
    }

    private static String response(long operationSeq, String status, String viewModel, String message) {
        try {
            var envelope = JSON.createObjectNode().put("operationSeq", operationSeq).put("status", status);
            envelope.set("viewModel", JSON.readTree(viewModel));
            if (message == null) envelope.putNull("error");
            else envelope.putObject("error").put("message", message);
            return JSON.writeValueAsString(envelope);
        } catch (JsonProcessingException error) { throw new IllegalStateException("Could not encode Practice response", error); }
    }

    private static void requireIdentity(JsonNode event, String field, JsonNode authoritative) {
        if (!event.path(field).isTextual() || !event.path(field).asText().equals(authoritative.asText()))
            throw new IllegalArgumentException("Practice event identity does not match the active question");
    }

    /** Resolve each function from its strong page-global reference, not a cached Java JSObject wrapper. */
    private Object callPage(String receiver, String method, String argument) {
        requireFxThread();
        var engine = view.getEngine();
        if (argument == null) return engine.executeScript("window." + receiver + "." + method + "()");
        var global = (JSObject) engine.executeScript("window");
        // receiver/method are private constant call sites; payload is passed as data, never evaluated.
        global.setMember("__quizforgeBridgeArgument", argument);
        try { return engine.executeScript("window." + receiver + "." + method + "(window.__quizforgeBridgeArgument)"); }
        finally { global.removeMember("__quizforgeBridgeArgument"); }
    }

    private JSObject activeCanvas() {
        requireFxThread();
        if (destroyed) throw new IllegalStateException("Shared Practice page has been destroyed");
        if (canvas == null || !practiceLoaded) throw new IllegalStateException("Shared Practice page is not ready");
        return canvas;
    }
    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Use Shared Practice on the JavaFX thread");
    }
}
