package io.quizforge.desktop.browser.javafx;

import io.quizforge.desktop.learning.SharedPracticeAdapter;
import io.quizforge.desktop.learning.SharedPracticeViewModel;

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

/** JavaFX practice backend. The shared adapter updates authoritative Core state. */
public final class SharedPracticeCanvasWebView implements io.quizforge.desktop.browser.PracticeLearningSurface {
    private static final java.util.concurrent.atomic.AtomicInteger LIVE_VIEWS = new java.util.concurrent.atomic.AtomicInteger();
    private static final DraftCanvasJsonCodec DRAFT_JSON = new DraftCanvasJsonCodec();
    private static final ObjectMapper JSON = io.quizforge.infrastructure.json.DocumentJson.mapper();
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
    private java.util.Map<String,Boolean> uiPreferences=java.util.Map.of();
    private java.util.function.Consumer<java.util.Map<String,Boolean>> onUiChange=preferences->{ };
    public void onUiChange(java.util.function.Consumer<java.util.Map<String,Boolean>> listener) {onUiChange=listener;listener.accept(uiPreferences);}
    private boolean currentSuspended;
    private final io.quizforge.desktop.ui.question.shared.QuestionPageActions pageActions;
    public void configurePageActions(java.util.function.Supplier<java.util.Map<String,Object>> state,java.util.function.BiFunction<String,Object,CompletionStage<Void>> command){pageActions.configure(state,command);}
    private String lastPersistedDraft;
    private io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode learningMode = io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode.DRAFT;
    private javafx.beans.value.ChangeListener<Worker.State> loadListener;

    public SharedPracticeCanvasWebView(SharedPracticeAdapter adapter) {
        this(adapter, () -> {});
    }
    public SharedPracticeCanvasWebView(SharedPracticeAdapter adapter, Runnable onChanged) {
        requireFxThread();
        this.adapter = Objects.requireNonNull(adapter);
        this.onChanged = Objects.requireNonNull(onChanged);
        pageActions=new io.quizforge.desktop.ui.question.shared.QuestionPageActions(view,
            ()->destroyed?null:adapter.viewModel().question().sessionQuestionId(),()->destroyed||currentSuspended);
        var page = Objects.requireNonNull(getClass().getResource("/editor/draft-canvas/shared-practice.html"),
                "Build the independent draft-canvas frontend before launching Shared Practice");
        pageUrl = page.toExternalForm();
        view.setContextMenuEnabled(false);
        loadListener = (observable, before, after) -> {
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
                    io.quizforge.desktop.extension.ExtensionManager.getDefault().installScripts(view);
                    window.setMember("practiceHost", host);
                    window.setMember("pageHost",pageActions);
                    practice.call("bindHost");
                } catch (RuntimeException failure) { ready.completeExceptionally(failure); }
            } else if (after == Worker.State.FAILED || after == Worker.State.CANCELLED) {
                ready.completeExceptionally(new IllegalStateException("Local Shared Practice page failed to load",
                        view.getEngine().getLoadWorker().getException()));
            }
        };
        view.getEngine().getLoadWorker().stateProperty().addListener(loadListener);
        LIVE_VIEWS.incrementAndGet();
        view.getEngine().load(pageUrl);
    }

    public boolean focusTarget(String targetId) { activeCanvas(); return Boolean.TRUE.equals(callPage("sharedPractice", "focusTarget", Objects.requireNonNull(targetId))); }
    public WebView view() { return view; }
    public CompletionStage<Void> ready() { return ready.minimalCompletionStage(); }
    public boolean isDestroyed() { return destroyed; }
    public boolean isReady() { return practiceLoaded; }
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
        callPage("sharedPractice", "restoreDraft", displayedDraftPayload().toString());
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
        callPage("sharedPractice", "suspendCurrent", null);
        currentSuspended = true;
        mutations.reset();
    }
    public CompletionStage<Void> reloadCurrent() {
        if (destroyed) throw new IllegalStateException("Practice surface destroyed");
        activeCanvas();
        mutations.reset();
        try {
            var payload = JSON.createObjectNode();
            payload.set("viewModel", JSON.readTree(adapter.viewModelJson()));
            payload.setAll(displayedDraftPayload());
            callPage("sharedPractice", "replaceCurrent", JSON.writeValueAsString(payload));
            currentSuspended = false;
            return CompletableFuture.completedFuture(null);
        } catch (JsonProcessingException failure) { return CompletableFuture.failedFuture(failure); }
    }
    public ActivePracticeSnapshot snapshot() { requireFxThread(); return adapter.snapshot(); }
    public String practiceJson() { requireFxThread(); return adapter.viewModelJson(); }
    public String getDraft() { activeCanvas(); return (String) callPage("draftCanvas", "getDraft", null); }
    public void loadDraft(String json) { activeCanvas(); callPage("draftCanvas", "loadDraft", Objects.requireNonNull(json)); }
    public void setMode(String mode) { activeCanvas(); callPage("draftCanvas", "setMode", Objects.requireNonNull(mode)); }
    public void setLearningMode(io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode mode) {
        activeCanvas(); callPage("sharedPractice", "setLearningMode", mode.name()); learningMode = mode;
        if (Boolean.getBoolean("quizforge.learning.diagnostics"))
            System.out.println("LEARNING_RESOURCES " + view.getEngine().executeScript(
                    "JSON.stringify({mode:window.draftCanvas.diagnostics().learningMode,runtime:window.sharedPractice.diagnostics(),canvas:window.draftCanvas.diagnostics()})"));
    }
    public static int liveViewCount() { return LIVE_VIEWS.get(); }
    public String diagnostics() {
        activeCanvas();
        return (String) view.getEngine().executeScript("JSON.stringify(window.draftCanvas.diagnostics())");
    }

    public void destroy() {
        requireFxThread();
        if (destroyed) return;
        saveBeforeClose();
        destroyed = true;
        LIVE_VIEWS.decrementAndGet();
        pageLoading = false;
        view.getEngine().getLoadWorker().stateProperty().removeListener(loadListener); loadListener = null;
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
        if (currentSuspended) return;
        if (practiceLoaded && Boolean.TRUE.equals(view.getEngine().executeScript("window.sharedPractice.isSubmitting()")))
            throw new IllegalStateException("答案正在提交，请稍后关闭");
        if (practiceLoaded && adapter.viewModel().question().state() != SharedPracticeViewModel.State.SUBMITTED)
            {
            try {
                var pending=JSON.readTree((String)view.getEngine().executeScript("JSON.stringify(window.sharedPractice.pendingAnswerIntent())"));
                if(pending!=null && !pending.isNull())applyAnswerIntent(pending,adapter.viewModel().question().type());
            }catch(JsonProcessingException invalid){throw new IllegalStateException("Cannot save pending text answer",invalid);}
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

    /** Semantic business events plus the focused editor's native clipboard bridge. */
    public final class PracticeHost {
        public void uiPreferences(String questionId,String encoded) {
            if(destroyed)return;
            try {
                var preferences=io.quizforge.desktop.ui.question.shared.QuestionUiPreferences.validate(
                        JSON.readValue(encoded,new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>() { }),false);
                Platform.runLater(()->{
                    if(destroyed)return;
                    try {
                        if(!questionId.equals(adapter.viewModel().question().sessionQuestionId()))return;
                        uiPreferences=preferences;onUiChange.accept(preferences);
                    } catch(IllegalStateException | IllegalArgumentException ignored) { /* The card may have been released. */ }
                });
            }catch(Exception ignored){ /* Invalid page presentation must not change host chrome. */ }
        }
        public String readClipboard() {
            requireFxThread();
            if(destroyed)return "{}";
            var clipboard=javafx.scene.input.Clipboard.getSystemClipboard();
            return JSON.createObjectNode().put("text",clipboard.hasString()?clipboard.getString():"")
                    .put("html",clipboard.hasHtml()?clipboard.getHtml():"").toString();
        }
        public boolean writeClipboard(String text,String html) {
            requireFxThread();
            if(destroyed)return false;
            var content=new javafx.scene.input.ClipboardContent();content.putString(text==null?"":text);
            if(html!=null&&!html.isBlank())content.putHtml(html);
            return javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        }
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
                callPage("sharedPractice", "restoreDraft", displayedDraftPayload().toString());
                practiceLoaded = true;
                ready.complete(null);
            } catch (RuntimeException failure) { ready.completeExceptionally(failure); }
        }

        public void onEvent(String json) {
            requireFxThread();
            if (destroyed || currentSuspended || !practiceLoaded) return;
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
            if (destroyed || currentSuspended) return;
            // Read at execution time, after every earlier FIFO mutation, rather than at enqueue time.
            var before = adapter.viewModelJson();
            String status;
            String viewModel;
            String message = null;
            io.quizforge.core.question.type.extension.ExtensionDataValidationException invalidData = null;
            io.quizforge.core.question.type.extension.ExtensionExecutionException executionFailure = null;
            try {
                JsonNode current = JSON.readTree(before);
                requireIdentity(event, "sessionId", current.path("session").path("sessionId"));
                requireIdentity(event, "sessionQuestionId", current.path("question").path("sessionQuestionId"));
                switch (event.path("type").asText()) {
                    case "ANSWER_CHANGED" -> applyAnswerIntent(event,current.path("question").path("type").asText());
                    case "DRAFT_CHANGED" -> {
                        var document = DraftCanvasJsonCodec.decode(event.path("document").toString());
                        if (learningMode != io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode.DRAFT
                                && !(event.path("initialLayout").isBoolean() && event.path("initialLayout").asBoolean() && initialLayoutOnly(document)))
                            throw new IllegalStateException("Practice annotations are locked");
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
                if (failure instanceof io.quizforge.core.question.type.extension.ExtensionDataValidationException invalid) invalidData = invalid;
                if (failure instanceof io.quizforge.core.question.type.extension.ExtensionExecutionException execution) executionFailure = execution;
            }
            // A single response envelope lets JS reject stale success AND stale error atomically.
            String draft = null;
            if ("SUCCESS".equals(status) && ("SUBMIT".equals(event.path("type").asText()) || "RETRY".equals(event.path("type").asText())))
                draft = DRAFT_JSON.encode(adapter.loadDisplayedDraft());
            if (draft != null) lastPersistedDraft = draft;
            callPage("sharedPractice", "applyResponse", response(operationSeq, status, viewModel, message, event.path("type").asText(), draft, invalidData, executionFailure));
            if ("SUCCESS".equals(status) && !"DRAFT_CHANGED".equals(event.path("type").asText()))
                Platform.runLater(() -> { if (!destroyed && practiceLoaded) onChanged.run(); });
        }
    }

    private void applyAnswerIntent(JsonNode event,String questionType) {
        if (io.quizforge.core.question.type.QuestionTypes.isExtension(questionType)) {
            var answer = event.get("answer");
            if (answer == null || !answer.isObject()) throw new IllegalArgumentException("Extension answer must be an object");
            adapter.extensionAnswerChanged(JSON.convertValue(answer, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>() { }));
            return;
        }
        throw new IllegalArgumentException("缺少对应题型扩展：" + questionType);
    }

    /** A layout declaration can initialize geometry once; it cannot edit a saved card/camera or annotations. */
    private boolean initialLayoutOnly(DraftCanvasDocument document) {
        var saved=adapter.findDisplayedDraft();
        if(saved.isPresent())return saved.get().equals(document); // Idempotent flush; no snapshot changes.
        var empty=DraftCanvasDocument.createEmpty();
        var width=document.questionCard().width();
        if(width<64 || width>8192)return false;
        var viewport=document.viewport();
        if(viewport.zoom()<.1 || viewport.zoom()>1 || Math.abs(viewport.x())>1_000_000 || Math.abs(viewport.y())>1_000_000)return false;
        var expected=new DraftCanvasDocument(empty.schemaVersion(),empty.layoutVersion(),viewport,
                new DraftCanvasDocument.QuestionCard(empty.questionCard().x(),empty.questionCard().y(),width),empty.strokes());
        return expected.equals(document);
    }

    private static String response(long operationSeq, String status, String viewModel, String message, String operationType, String draft,
            io.quizforge.core.question.type.extension.ExtensionDataValidationException invalidData,
            io.quizforge.core.question.type.extension.ExtensionExecutionException executionFailure) {
        try {
            var envelope = JSON.createObjectNode().put("operationSeq", operationSeq).put("status", status);
            envelope.set("viewModel", JSON.readTree(viewModel));
            envelope.put("operationType", operationType);
            if (draft != null) envelope.set("draftDocument", JSON.readTree(draft));
            if (message == null) envelope.putNull("error");
            else {
                var error = envelope.putObject("error").put("message", message);
                if (invalidData != null) {error.put("code","DATA_VALIDATION_FAILED");error.set("issues",JSON.valueToTree(invalidData.issues()));}
                if (executionFailure != null) error.put("code", executionFailure.code());
            }
            return JSON.writeValueAsString(envelope);
        } catch (JsonProcessingException error) { throw new IllegalStateException("Could not encode Practice response", error); }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode displayedDraftPayload() {
        var saved = adapter.findDisplayedDraft();
        lastPersistedDraft = DRAFT_JSON.encode(saved.orElseGet(DraftCanvasDocument::createEmpty));
        try {
            var payload = JSON.createObjectNode().put("hasSavedDraft", saved.isPresent());
            payload.set("document", JSON.readTree(lastPersistedDraft));
            return payload;
        } catch (JsonProcessingException failure) { throw new IllegalStateException("Could not encode displayed draft", failure); }
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
