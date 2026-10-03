package io.quizforge.desktop.poc.sharedpractice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.practice.ActivePracticeSnapshot;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.infrastructure.persistence.practice.DraftCanvasJsonCodec;
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
    private static final DraftCanvasJsonCodec DRAFT_JSON = new DraftCanvasJsonCodec();
    private static final ObjectMapper JSON = new ObjectMapper();
    private final WebView view = new WebView();
    private final SharedPracticeAdapter adapter;
    private CompletableFuture<Void> ready = new CompletableFuture<>();
    private final String pageUrl;
    private boolean pageLoading = true;
    private final Runnable onChanged;
    private final java.util.Map<String, CompletableFuture<Void>> flushes = new java.util.HashMap<>();
    private long flushId;
    // WebKit holds a weak reference to Java objects; retain the narrow bridge for this page's lifetime.
    private final PracticeHost host = new PracticeHost();
    private final PracticeMutationQueue mutations = new PracticeMutationQueue();
    private JSObject practice;
    private JSObject canvas;
    private boolean destroyed;
    private boolean practiceLoaded;
    private String lastPersistedDraft;

    public SharedPracticeCanvasWebView(SharedPracticeAdapter adapter) {
        this(adapter, () -> {});
    }
    public SharedPracticeCanvasWebView(SharedPracticeAdapter adapter, Runnable onChanged) {
        requireFxThread();
        this.adapter = Objects.requireNonNull(adapter);
        this.onChanged = Objects.requireNonNull(onChanged);
        var page = Objects.requireNonNull(getClass().getResource("/editor/draft-canvas/shared-practice.html"),
                "Build the independent draft-canvas frontend before launching Shared Practice");
        pageUrl = page.toExternalForm();
        view.setContextMenuEnabled(false);
        view.getEngine().getLoadWorker().stateProperty().addListener((observable, before, after) -> {
            if (destroyed || !pageLoading) return;
            String location = view.getEngine().getLocation();
            // WebKit rewrites local URL spelling (including drive letter case and escaping).
            // Ignore the released blank scope, then validate the actual bridges on success.
            if (location == null || location.isBlank() || "about:blank".equals(location)) return;
            if (after == Worker.State.SUCCEEDED) {
                try {
                    var window = (JSObject) view.getEngine().executeScript("window");
                    if (!(window.getMember("sharedPractice") instanceof JSObject practiceBridge)
                            || !(window.getMember("draftCanvas") instanceof JSObject canvasBridge))
                        throw new IllegalStateException("Local page bridges are missing");
                    practice = practiceBridge;
                    canvas = canvasBridge;
                    window.setMember("practiceHost", host);
                    practice.call("bindHost");
                } catch (RuntimeException failure) { ready.completeExceptionally(failure); }
            } else if (after == Worker.State.FAILED || after == Worker.State.CANCELLED) {
                ready.completeExceptionally(new IllegalStateException("Local Shared Practice page failed to load",
                        view.getEngine().getLoadWorker().getException()));
            }
        });
        view.getEngine().load(pageUrl);
    }

    public WebView view() { return view; }
    public CompletionStage<Void> ready() { return ready.minimalCompletionStage(); }
    public boolean isDestroyed() { return destroyed; }
    /** Same question, same JS scope: refresh from Core without adding bridge listeners or losing sequence state. */
    public CompletionStage<Void> refreshCurrent() {
        activeCanvas();
        try {
            var displayed = JSON.readTree((String)view.getEngine().executeScript("JSON.stringify(window.sharedPractice.getViewState())"));
            var current = adapter.viewModel();
            if (!displayed.path("session").path("sessionId").asText().equals(current.session().sessionId())
                    || !displayed.path("question").path("sessionQuestionId").asText().equals(current.question().sessionQuestionId()))
                return reloadCurrent();
        } catch (JsonProcessingException failure) { throw new IllegalStateException("Could not read displayed practice identity", failure); }
        callPage("sharedPractice", "refreshCurrent", adapter.viewModelJson());
        lastPersistedDraft = DRAFT_JSON.encode(adapter.loadDraft());
        callPage("sharedPractice", "restoreDraft", lastPersistedDraft);
        return CompletableFuture.completedFuture(null);
    }
    public CompletionStage<Void> flushPendingDraft() {
        activeCanvas();
        String id = Long.toString(++flushId);
        var pending = new CompletableFuture<Void>(); flushes.put(id, pending);
        try { callPage("sharedPractice", "flushToHost", id); }
        catch (RuntimeException failure) { flushes.remove(id); pending.completeExceptionally(failure); }
        return pending.minimalCompletionStage();
    }
    /** Called only after the old scope's save barrier; the WebView object survives navigation. */
    public void unloadCurrent() {
        requireFxThread();
        pageLoading = false;
        disposePage(); practiceLoaded = false; practice = null; canvas = null;
        mutations.reset();
        ready.completeExceptionally(new IllegalStateException("Practice scope released"));
        view.getEngine().load(null);
    }
    public CompletionStage<Void> reloadCurrent() {
        if (destroyed) throw new IllegalStateException("Practice surface destroyed");
        unloadCurrent();
        ready = new CompletableFuture<>();
        view.getEngine().load(pageUrl);
        pageLoading = true;
        return ready();
    }
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
        saveBeforeClose();
        destroyed = true;
        pageLoading = false;
        mutations.clear();
        try { disposePage(); }
        finally {
            practice = null;
            canvas = null;
            ready.completeExceptionally(new IllegalStateException("Shared Practice page was destroyed before readiness"));
            view.getEngine().load(null);
        }
    }
    public void saveBeforeClose() {
        requireFxThread();
        if (practiceLoaded && Boolean.TRUE.equals(view.getEngine().executeScript("window.sharedPractice.isSubmitting()")))
            throw new IllegalStateException("答案正在提交，请稍后关闭");
        if (practiceLoaded && adapter.viewModel().question().state() != SharedPracticeViewModel.State.SUBMITTED)
            {
            var document = DraftCanvasJsonCodec.decode(getDraft());
            String json=DRAFT_JSON.encode(document);
            if (!json.equals(lastPersistedDraft)) {adapter.saveDraft(document);lastPersistedDraft=json;}
        }
    }
    private void disposePage() {
        if (practice != null || canvas != null) view.getEngine().executeScript(
                "if(window.sharedPractice) window.sharedPractice.destroy();"
                + "if(window.draftCanvas) window.draftCanvas.destroy();");
        flushes.values().forEach(future -> future.completeExceptionally(new IllegalStateException("Practice page released")));
        flushes.clear();
    }

    /** This is the complete JS -> Java business surface: ready plus a semantic event envelope. */
    public final class PracticeHost {
        public void onFlushCompleted(String id, boolean success, String message) {
            requireFxThread();
            var pending = flushes.remove(id);
            if (pending == null || destroyed) return;
            // Finish the JS callback stack before a completion switches or releases its page.
            Platform.runLater(() -> {
                if (success) pending.complete(null);
                else pending.completeExceptionally(new IllegalStateException(message));
            });
        }
        public void ready() {
            requireFxThread();
            if (destroyed || practiceLoaded) return;
            try {
                callPage("sharedPractice", "loadPractice", adapter.viewModelJson());
                lastPersistedDraft = DRAFT_JSON.encode(adapter.loadDraft());
                callPage("sharedPractice", "restoreDraft", lastPersistedDraft);
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
                    case "DRAFT_CHANGED" -> {
                        var document = DraftCanvasJsonCodec.decode(event.path("document").toString());
                        adapter.saveDraft(document);
                        lastPersistedDraft = DRAFT_JSON.encode(document);
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
            String draft = null;
            if ("SUCCESS".equals(status) && ("SUBMIT".equals(event.path("type").asText()) || "RETRY".equals(event.path("type").asText())))
                // Both Core operations delete the active row atomically. Avoid a second DB read after commit.
                draft = DRAFT_JSON.encode(DraftCanvasDocument.createEmpty());
            if (draft != null) lastPersistedDraft = draft;
            callPage("sharedPractice", "applyResponse", response(operationSeq, status, viewModel, message, event.path("type").asText(), draft));
            if ("SUCCESS".equals(status) && !"DRAFT_CHANGED".equals(event.path("type").asText()))
                Platform.runLater(() -> { if (!destroyed && practiceLoaded) onChanged.run(); });
        }
    }

    private static String response(long operationSeq, String status, String viewModel, String message, String operationType, String draft) {
        try {
            var envelope = JSON.createObjectNode().put("operationSeq", operationSeq).put("status", status);
            envelope.set("viewModel", JSON.readTree(viewModel));
            envelope.put("operationType", operationType);
            if (draft != null) envelope.set("draftDocument", JSON.readTree(draft));
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
