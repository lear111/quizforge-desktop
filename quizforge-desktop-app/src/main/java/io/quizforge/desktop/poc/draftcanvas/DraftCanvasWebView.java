package io.quizforge.desktop.poc.draftcanvas;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** Isolated local-page host. Geometry, editing and serialization belong to JavaScript. */
public final class DraftCanvasWebView {
    private final WebView view = new WebView();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private JSObject bridge;
    private boolean destroyed;

    public DraftCanvasWebView() {
        requireFxThread();
        var page = Objects.requireNonNull(getClass().getResource("/editor/draft-canvas/draft-canvas.html"),
                "Build the independent draft-canvas frontend before launching its POC");
        view.getEngine().getLoadWorker().stateProperty().addListener((observable, before, after) -> {
            if (destroyed) return;
            if (after == Worker.State.SUCCEEDED) {
                try {
                    bridge = (JSObject) view.getEngine().executeScript("window.draftCanvas");
                    if (bridge == null) throw new IllegalStateException("Draft Canvas bridge is missing");
                    ready.complete(null);
                } catch (RuntimeException failure) { ready.completeExceptionally(failure); }
            } else if (after == Worker.State.FAILED || after == Worker.State.CANCELLED) {
                ready.completeExceptionally(new IllegalStateException("Local Draft Canvas page failed to load",
                        view.getEngine().getLoadWorker().getException()));
            }
        });
        view.setContextMenuEnabled(false);
        view.getEngine().load(page.toExternalForm());
    }

    public WebView view() { return view; }
    public CompletionStage<Void> ready() { return ready.minimalCompletionStage(); }
    public String getDraft() { return (String) activeBridge().call("getDraft"); }
    public void loadDraft(String json) { activeBridge().call("loadDraft", Objects.requireNonNull(json)); }
    public void setMode(String mode) { activeBridge().call("setMode", Objects.requireNonNull(mode)); }
    public String diagnostics() {
        activeBridge();
        return (String) view.getEngine().executeScript("JSON.stringify(window.draftCanvas.diagnostics())");
    }

    /** Idempotent; releases pointer listeners through JS and then the loaded page. */
    public void destroy() {
        requireFxThread();
        if (destroyed) return;
        if (bridge != null) bridge.call("destroy");
        destroyed = true;
        bridge = null;
        ready.completeExceptionally(new IllegalStateException("Draft Canvas was destroyed before readiness"));
        view.getEngine().load(null);
    }

    private JSObject activeBridge() {
        requireFxThread();
        if (destroyed) throw new IllegalStateException("Draft Canvas has been destroyed");
        if (bridge == null) throw new IllegalStateException("Draft Canvas is not ready");
        return bridge;
    }
    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Use Draft Canvas on the JavaFX thread");
    }
}
