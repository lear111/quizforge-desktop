package io.quizforge.desktop.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.desktop.learning.SharedContent;
import io.quizforge.desktop.ui.content.StagedContentResource;
import io.quizforge.desktop.ui.content.document.canvas.CanvasEditorWindow;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.infrastructure.extension.ExtensionDevelopmentSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import netscape.javascript.JSObject;

/** Transactional source preview. Candidate pages replace the last good page only after validation. */
public final class ExtensionDevelopmentWindow implements AutoCloseable {
    private final Stage stage = new Stage();
    private final BorderPane root = new BorderPane();
    private final Label status = new Label("正在读取开发目录…");
    private final Button refresh = new Button("刷新");
    private final ExtensionDevelopmentSource source;
    private final ObjectMapper json = io.quizforge.infrastructure.json.DocumentJson.mapper();
    private final Map<String,StagedContentResource> resources = new LinkedHashMap<>();
    private final Map<String,CompletableFuture<String>> flushes = new LinkedHashMap<>();
    private final Host host = new Host();
    private final Map<WebView,ExtensionDevelopmentRulesBridge> ruleBridges = new LinkedHashMap<>();
    private ExtensionDevelopmentWatcher watcher;
    private ExtensionDevelopmentSource.Candidate current;
    private WebView active;
    private WebView candidate;
    private boolean closed;
    private boolean loading;
    private boolean refreshAgain;
    private long sequence;

    public ExtensionDevelopmentWindow(Window owner, Path directory) {
        requireFx(); source = new ExtensionDevelopmentSource(directory);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("题型扩展开发 · " + source.directory().getFileName());
        stage.setWidth(1260); stage.setHeight(850); stage.setMinWidth(900); stage.setMinHeight(620);
        status.setWrapText(true); status.setId("extension-development-status"); refresh.setId("extension-development-refresh");
        refresh.setOnAction(event -> refresh());
        var path = new Label(source.directory().toString()); path.setStyle("-fx-text-fill:#777;");
        var spacer = new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);
        var bar = new HBox(14,path,spacer,refresh); bar.setPadding(new Insets(10,14,10,14));
        root.setTop(bar); root.setBottom(status); BorderPane.setMargin(status,new Insets(8,14,10,14));
        var scene = new Scene(root); UiTheme.apply(scene); stage.setScene(scene);
        stage.setOnHidden(event -> close());
        try { watcher = new ExtensionDevelopmentWatcher(source.directory(), () -> Platform.runLater(() -> { if (!closed) refresh(); })); }
        catch (java.io.IOException failure) { status.setText("文件监听不可用，仍可手动刷新：" + failure.getMessage()); }
        refresh();
    }
    public static ExtensionDevelopmentWindow open(Window owner, Path directory) {
        var window = new ExtensionDevelopmentWindow(owner,directory); window.stage.show(); return window;
    }
    public Stage stage() { return stage; }
    public WebView view() { return active; }
    public String status() { return status.getText(); }
    public String revision() { return current == null ? null : current.revision(); }
    public boolean loading() { return loading; }
    public Path directory() { return source.directory(); }
    public void refresh() {
        requireFx(); if (closed) return;
        if (loading) { refreshAgain = true; return; }
        loading = true; refresh.setDisable(true); status.setText("正在检查源文件…");
        CompletableFuture.supplyAsync(() -> {
            try { return source.load(); } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }).whenComplete((snapshot,failure) -> Platform.runLater(() -> {
            if (closed) return;
            if (failure != null) { fail(failure); return; }
            if (current != null && current.revision().equals(snapshot.revision())) { finish("已是最新 · " + current.manifest().name()); return; }
            captureState().whenComplete((previous,flushFailure) -> Platform.runLater(() -> {
                if (closed) return;
                if (flushFailure != null) { fail(flushFailure); return; }
                try { mount(snapshot,previous); } catch (RuntimeException mountFailure) { fail(mountFailure); }
            }));
        }));
    }
    private CompletableFuture<String> captureState() {
        if (active == null) return CompletableFuture.completedFuture(null);
        String request = Long.toString(++sequence); var result = new CompletableFuture<String>(); flushes.put(request,result);
        var timeout = new PauseTransition(Duration.seconds(8));
        timeout.setOnFinished(event -> result.completeExceptionally(new IllegalStateException("编辑器刷新保存超时；保留上一次预览")));
        result.whenComplete((value,failure) -> { timeout.stop(); flushes.remove(request); });
        try { active.getEngine().executeScript("window.extensionWorkbench.flushToHost('" + request + "')"); if (!result.isDone()) timeout.play(); }
        catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }
    private void mount(ExtensionDevelopmentSource.Candidate snapshot, String previous) {
        boolean reset = current != null && !current.rulesRevision().equals(snapshot.rulesRevision());
        var page = Objects.requireNonNull(getClass().getResource("/editor/draft-canvas/extension-workbench.html"),"Build the extension workbench frontend first");
        var next = new WebView(); candidate = next; next.setContextMenuEnabled(false);
        String loadRequest="load-"+(++sequence);
        // Candidate rules initialize first, then its isolated page; their startup budgets are separate.
        var timeout = new PauseTransition(Duration.seconds(100));
        timeout.setOnFinished(event -> { if (!closed && candidate == next) { candidate = null;var pending=flushes.remove(loadRequest);if(pending!=null)pending.completeExceptionally(new IllegalStateException("开发预览加载超时"));dispose(next); fail(new IllegalStateException("开发预览加载超时；保留上一次预览")); } });
        next.getEngine().setCreatePopupHandler(features -> null);
        next.getEngine().getLoadWorker().stateProperty().addListener((property,before,state) -> {
            if (closed || candidate != next) return;
            if (state == Worker.State.SUCCEEDED) {
                try {
                    var window = (JSObject)next.getEngine().executeScript("window");
                    ExtensionPageBridge.install(next);
                    var rulesBridge=new ExtensionDevelopmentRulesBridge(next.getEngine(),snapshot.assets());
                    ruleBridges.put(next,rulesBridge);window.setMember("nativeRulesHost",rulesBridge);
                    window.setMember("workbenchHost",host); window.setMember("editorHost",host);
                    window.setMember("__developmentBundle",snapshot.bundleJson()); window.setMember("__developmentPrevious",previous);
                    window.setMember("__developmentOptions",json.writeValueAsString(Map.of("resetAnswers",reset,"revision",snapshot.revision())));
                    String request=loadRequest;
                    var loaded=new CompletableFuture<String>();flushes.put(request,loaded);
                    loaded.whenComplete((value,failure)->Platform.runLater(()->{
                        flushes.remove(request);timeout.stop();if(closed||candidate!=next)return;
                        if(failure!=null){candidate=null;dispose(next);fail(failure);return;}
                        WebView old=active;active=next;candidate=null;current=snapshot;root.setCenter(next);
                        if(old!=null)dispose(old);
                        finish("开发预览已更新 · "+snapshot.manifest().name()+" · "+snapshot.revision().substring(0,10)+(reset?" · 规则变化，测试作答已重置":""));
                    }));
                    try { next.getEngine().executeScript("window.extensionWorkbench.load(JSON.parse(window.__developmentBundle),window.__developmentPrevious,JSON.parse(window.__developmentOptions)).then(value=>window.workbenchHost.flushed('"+request+"',value),error=>window.workbenchHost.flushFailed('"+request+"',error.message))"); }
                    finally { window.removeMember("__developmentBundle"); window.removeMember("__developmentPrevious"); window.removeMember("__developmentOptions"); }
                } catch (Exception failure) { candidate = null; dispose(next); fail(failure); }
            } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                timeout.stop();
                candidate = null; dispose(next); fail(new IllegalStateException("开发预览页面加载失败"));
            }
        });
        next.getEngine().load(page.toExternalForm()); timeout.play();
    }
    private void fail(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        finish("刷新失败，" + (active == null ? "等待修复源文件" : "保留上一次可用预览") + "：" + failure.getMessage());
    }
    private void finish(String message) {
        status.setText(message); loading = false; refresh.setDisable(false);
        if (refreshAgain) { refreshAgain = false; Platform.runLater(this::refresh); }
    }
    private void dispose(WebView page) {
        var rulesBridge=ruleBridges.remove(page);if(rulesBridge!=null)rulesBridge.close();
        try { page.getEngine().executeScript("if(window.extensionWorkbench)window.extensionWorkbench.destroy&&window.extensionWorkbench.destroy();window.workbenchHost=null;window.editorHost=null;"); }
        catch (RuntimeException ignored) { } page.getEngine().load(null);
    }
    @Override public void close() {
        requireFx(); if (closed) return; closed = true;
        if (watcher != null) watcher.close();
        for (var pending : List.copyOf(flushes.values())) pending.completeExceptionally(new IllegalStateException("开发窗口已关闭")); flushes.clear();
        var pendingPage = candidate; candidate = null; if (pendingPage != null) dispose(pendingPage);
        if (active != null) dispose(active); active = null; resources.clear(); root.setCenter(null);
        if (stage.isShowing()) stage.close();
    }
    public final class Host {
        public void flushed(String id,String state) { var pending = flushes.get(id); if (pending != null) pending.complete(state); }
        public void flushFailed(String id,String message) { var pending = flushes.get(id); if (pending != null) pending.completeExceptionally(new IllegalArgumentException(message)); }
        public String editContent(String encoded) {
            try {
                var content = QuestionContentData.decode(json.readValue(encoded,Object.class));
                QuestionResourceInput input = resource -> { var staged = resources.get(resource.id()); return staged == null ? null : staged.open(); };
                var edited = CanvasEditorWindow.openEditor(stage,"开发预览 · 编辑富文本",content,resources.values().stream().map(StagedContentResource::resource).toList(),input);
                if (!edited.saved()) return encoded;
                for (var resource : edited.addedResources()) resources.put(resource.resource().id(),resource);
                return json.writeValueAsString(QuestionContentData.encode(edited.content()));
            } catch (Exception failure) { status.setText("富文本编辑失败：" + failure.getMessage()); return encoded; }
        }
        public String resolveContent(String encoded) {
            try {
                var raw = json.readValue(encoded,Object.class); var ids = QuestionContentData.resourceIds(QuestionContentData.decode(raw));
                var bytes = new LinkedHashMap<String,String>(); var metadata = new ArrayList<Map<String,String>>();
                for (String id : ids) {
                    var resource = resources.get(id); if (resource == null) throw new IllegalArgumentException("开发预览资源缺失：" + id);
                    bytes.put(id,Base64.getEncoder().encodeToString(resource.bytes())); metadata.add(Map.of("id",id,"mediaType",resource.resource().mediaType()));
                }
                return json.writeValueAsString(SharedContent.read(raw,Map.of("resourceData",bytes,"resources",metadata)));
            } catch (Exception failure) { throw new IllegalArgumentException("无法显示开发预览富文本",failure); }
        }
    }
    private static void requireFx() { if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Use the JavaFX application thread"); }
}
