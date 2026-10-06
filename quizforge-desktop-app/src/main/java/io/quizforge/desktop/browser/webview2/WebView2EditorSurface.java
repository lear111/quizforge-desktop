package io.quizforge.desktop.browser.webview2;

import com.fasterxml.jackson.databind.JsonNode;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.infrastructure.json.DocumentJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/** Tab-owned authoring browser. All messages reach the existing scoped editor controller on FX. */
public final class WebView2EditorSurface implements AutoCloseable {
    private final Pane view = new Pane();
    private final Supplier<Map<String,Object>> state;
    private final Consumer<JsonNode> events;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private WebView2Browser browser;
    private Path directory;
    private Runnable unregister = () -> {};
    private boolean closed, booted;
    private final java.util.List<Map<String,Object>> updates = new java.util.ArrayList<>();

    public WebView2EditorSurface(Supplier<Map<String,Object>> state, Consumer<JsonNode> events) {
        this.state=state;this.events=events;view.setMinSize(0,0);
        view.sceneProperty().addListener((o,b,a)->{
            if(a!=null){a.windowProperty().addListener((ignored,before,after)->attach());attach();}
        });
    }
    private void attach() {
        if(closed||browser!=null||view.getScene()==null||!(view.getScene().getWindow() instanceof Stage stage))return;
        if(!stage.isShowing()){stage.showingProperty().addListener((o,b,a)->{if(a)attach();});return;}
        try {
            directory=WebView2TemporaryDirectories.create("editor-");
            browser=new WebView2Browser(stage,view,directory,WebView2Browser.Page.EDITOR,
                    message->Platform.runLater(()->receive(message)));
            unregister=ExtensionManager.getDefault().registerMessagePage(message->{
                if(closed)return;
                if("stop".equals(message.get("kind"))){close();return;}
                if(!booted)updates.add(message);else post(message);
            });
        } catch(Exception failure) {
            ready.completeExceptionally(failure);
            events.accept(DocumentJson.mapper().valueToTree(Map.of("kind","native-error","message",failure.getMessage()==null?"编辑页无法启动":failure.getMessage())));
            close();
        }
    }
    private void receive(JsonNode message) {
        if(closed)return;
        switch(message.path("kind").asText()) {
            case "boot" -> {
                booted=true;
                try {
                    post(Map.of("kind","bootstrap","packages",DocumentJson.mapper().readTree(ExtensionManager.getDefault().currentPackagesJson()),"state",state.get()));
                    for(var update:updates)post(update);updates.clear();
                } catch(Exception failure) {
                    ready.completeExceptionally(failure);
                    events.accept(DocumentJson.mapper().valueToTree(Map.of("kind","native-error","message",failure.getMessage()==null?"编辑页无法初始化":failure.getMessage())));
                    close();
                }
            }
            case "editor-ready" -> {ready.complete(null);events.accept(message);}
            case "native-error","page-error" -> {
                ready.completeExceptionally(new IllegalStateException(message.toString()));events.accept(message);
            }
            default -> events.accept(message);
        }
    }
    public Pane view(){return view;}
    public CompletionStage<Void> ready(){return ready.minimalCompletionStage();}
    public void post(Object message){if(closed||browser==null||!booted)throw new IllegalStateException("编辑页面未就绪或已关闭");browser.post(message);}
    public void shell(String method,Object value){if(booted&&!closed)post(Map.of("kind","shell","method",method,"value",value));}
    public void flush(String id){post(Map.of("kind","flush","id",id));}
    @Override public void close(){
        if(closed)return;closed=true;unregister.run();if(browser!=null)browser.close();
        WebView2TemporaryDirectories.release(directory,browser==null?CompletableFuture.completedFuture(null):browser.closeCompletion());
        ready.completeExceptionally(new IllegalStateException("编辑页面已关闭"));updates.clear();
    }
    WebView2Browser browser(){return browser;}
}
