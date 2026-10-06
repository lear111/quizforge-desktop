package io.quizforge.desktop.browser.webview2;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import io.quizforge.infrastructure.json.DocumentJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Windows learning backend. Native WebView2 owns painting and input; no screenshot transport. */
public final class WebView2Browser implements AutoCloseable {
    public enum Page { PRACTICE("webview2-practice"), EDITOR("webview2-editor"), HISTORY("webview2-history");
        private final String resource;Page(String resource){this.resource=resource;}
    }
    interface Api extends Library {
        long qf_create(HWND parent, WString assets, WString profile);
        int qf_post(long view, WString json);
        int qf_eval(long view, long request, WString source);
        int qf_eval_frame(long view, long request, WString source);
        int qf_mouse(long view,int action,int x,int y,int held);
        int qf_cdp(long view, long request, WString method, WString parameters);
        int qf_cdp_session(long view,long request,WString session,WString method,WString parameters);
        int qf_bounds(long view, int x, int y, int width, int height);
        int qf_visible(long view,int visible);
        int qf_visibility(long view);
        int qf_poll(long view, char[] buffer, int capacity);
        int qf_close(long view);
        int qf_browser_process(long view);
        int qf_exists(long view);
    }
    private final Api api;
    private final long id;
    private final ScheduledExecutorService reader = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "qf-webview2-messages"); thread.setDaemon(true); return thread;
    });
    private final Map<Long, CompletableFuture<JsonNode>> evaluations = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AnimationTimer layout;
    private volatile boolean closed;
    private final WebView2MessageAssembler messages = new WebView2MessageAssembler();
    private final CompletableFuture<Void> closeCompletion = new CompletableFuture<>();

    /** The supplied stage must be visible and have a unique title. Attach only to this process's window. */
    public WebView2Browser(Stage stage, Region area, Path directory, Consumer<JsonNode> events) throws IOException {
        this(stage,area,directory,Page.PRACTICE,events);
    }
    public WebView2Browser(Stage stage, Region area, Path directory, Page page, Consumer<JsonNode> events) throws IOException {
        if (!Platform.isFxApplicationThread() || !stage.isShowing()) throw new IllegalStateException("Attach on JavaFX after showing the stage");
        if (!System.getProperty("os.name", "").startsWith("Windows")) throw new UnsupportedOperationException("WebView2 requires Windows");
        var parent = User32.INSTANCE.FindWindow(null, stage.getTitle());
        if (parent == null) throw new IllegalStateException("JavaFX native window is missing");
        var pid = new com.sun.jna.ptr.IntByReference(); User32.INSTANCE.GetWindowThreadProcessId(parent, pid);
        if (Integer.toUnsignedLong(pid.getValue()) != ProcessHandle.current().pid()) throw new IllegalStateException("Window does not belong to this application");
        directory = directory.toAbsolutePath().normalize();
        var assets = Files.createDirectories(directory.resolve("assets"));
        var profile = Files.createDirectories(directory.resolve("browser-profile"));
        // Native resource interception exposes exactly three fixed aliases per browser.
        // Only the trusted host selects the packaged page; extensions cannot supply paths.
        for (var suffix : java.util.List.of("html", "js", "css")) {
            String file="webview2-practice."+suffix;
            try (var input = getClass().getResourceAsStream("/editor/draft-canvas/" + page.resource+"."+suffix)) {
                if (input == null) throw new IOException("Build the WebView2 frontend first: " + file);
                Files.copy(input, assets.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        var dll = Path.of(System.getProperty("quizforge.webview2.library", "target/webview2-native/quizforge_webview2.dll")).toAbsolutePath();
        if (!Files.isRegularFile(dll)) throw new IOException("Run tools/Build-WebView2.ps1 first");
        api = Native.load(dll.toString(), Api.class);
        id = api.qf_create(parent, new WString(assets.toString()), new WString(profile.toString()));
        if (id == 0) throw new IOException("Native WebView2 could not attach");
        layout = new AnimationTimer() {
            private String previous = "";
            private Boolean wasVisible;
            @Override public void handle(long now) {
                if (closed) return;
                boolean visible=area.getScene()!=null&&area.getScene().getWindow()==stage&&stage.isShowing();
                for(javafx.scene.Node node=area;node!=null;node=node.getParent())visible&=node.isVisible();
                if(!java.util.Objects.equals(wasVisible,visible)&&api.qf_visible(id,visible?1:0)!=0)wasVisible=visible;
                if(!visible)return;
                RECT client = new RECT(); if (!User32.INSTANCE.GetClientRect(parent, client)) return;
                var scene = area.getScene(); if (scene.getWidth() <= 0 || scene.getHeight() <= 0) return;
                Bounds bounds = area.localToScene(area.getBoundsInLocal());
                double xScale = (client.right - client.left) / scene.getWidth(), yScale = (client.bottom - client.top) / scene.getHeight();
                int x = (int)Math.round(bounds.getMinX()*xScale), y = (int)Math.round(bounds.getMinY()*yScale);
                int width = Math.max(1,(int)Math.round(bounds.getWidth()*xScale)), height = Math.max(1,(int)Math.round(bounds.getHeight()*yScale));
                String current=x+":"+y+":"+width+":"+height;
                if (!current.equals(previous) && api.qf_bounds(id,x,y,width,height)!=0) previous=current;
            }
        }; layout.start();
        reader.scheduleWithFixedDelay(() -> {
            if (closed) return;
            try {
                messages.expire();
                char[] buffer = new char[8192];
                for (int count=0; count<32; count++) {
                    int size=api.qf_poll(id,buffer,buffer.length);
                    if(size<0){if(-size>8*1024*1024+1)throw new IOException("Oversized browser event");buffer=new char[-size];size=api.qf_poll(id,buffer,buffer.length);}
                    if(size<=0)break;
                    JsonNode message=messages.accept(DocumentJson.mapper().readTree(new String(buffer,0,size)));
                    if(message==null)continue;
                    if("evaluation".equals(message.path("kind").asText())) {
                        var pending=evaluations.remove(message.path("id").asLong());
                        if(pending!=null){if(message.path("ok").asBoolean())pending.complete(message.path("value"));else pending.completeExceptionally(new IOException("Browser evaluation failed"));}
                    } else events.accept(message);
                }
            } catch(Exception failure) { messages.reset();events.accept(DocumentJson.mapper().valueToTree(Map.of("kind","native-error","code","MESSAGE_FAILED"))); }
        },0,10,TimeUnit.MILLISECONDS);
    }
    public void post(Object message) {
        if(closed)throw new IllegalStateException("Browser closed");
        try {
            String encoded=DocumentJson.mapper().writeValueAsString(message);
            if(encoded.length()>WebView2MessageAssembler.MAX_MESSAGE_CHARACTERS)throw new IllegalArgumentException("Browser message exceeds 128 Mi characters");
            if(api.qf_post(id,new WString(encoded))==0)throw new IllegalStateException("Browser is unavailable or overloaded");
        }
        catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalArgumentException(invalid);}
    }
    /** Trusted host diagnostics only. This method is never exposed to extensions. */
    public CompletionStage<JsonNode> evaluate(String source) {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Browser closed"));
        long request=sequence.incrementAndGet();var pending=new CompletableFuture<JsonNode>();evaluations.put(request,pending);
        if(api.qf_eval(id,request,new WString(source))==0){evaluations.remove(request);pending.completeExceptionally(new IllegalStateException("Browser unavailable"));}
        pending.orTimeout(Duration.ofSeconds(5).toMillis(),TimeUnit.MILLISECONDS).whenComplete((value,error)->evaluations.remove(request,pending));
        return pending.minimalCompletionStage();
    }
    CompletionStage<JsonNode> devTools(String method,Object parameters) {
        return devTools("",method,parameters);
    }
    CompletionStage<JsonNode> devTools(String session,String method,Object parameters) {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Browser closed"));
        long request=sequence.incrementAndGet();var pending=new CompletableFuture<JsonNode>();evaluations.put(request,pending);
        try {if(api.qf_cdp_session(id,request,new WString(session),new WString(method),new WString(DocumentJson.mapper().writeValueAsString(parameters)))==0)throw new IllegalStateException("Browser unavailable");}
        catch(Exception failure){evaluations.remove(request);pending.completeExceptionally(failure);}
        pending.orTimeout(5000,TimeUnit.MILLISECONDS).whenComplete((value,error)->evaluations.remove(request,pending));return pending.minimalCompletionStage();
    }
    CompletionStage<JsonNode> evaluateFrame(String source) {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Browser closed"));
        long request=sequence.incrementAndGet();var pending=new CompletableFuture<JsonNode>();evaluations.put(request,pending);
        if(api.qf_eval_frame(id,request,new WString(source))==0){evaluations.remove(request);pending.completeExceptionally(new IllegalStateException("Browser unavailable"));}
        pending.orTimeout(5000,TimeUnit.MILLISECONDS).whenComplete((value,error)->evaluations.remove(request,pending));return pending.minimalCompletionStage();
    }
    void mouse(int action,double x,double y,boolean held) {
        if(closed||api.qf_mouse(id,action,(int)Math.round(x),(int)Math.round(y),held?1:0)==0)throw new IllegalStateException("Browser input unavailable");
    }
    boolean nativeVisible(){return !closed&&api.qf_visibility(id)!=0;}
    /** Completes only after the native controller releases and its browser process exits. */
    public CompletionStage<Void> closeCompletion(){return closeCompletion.minimalCompletionStage();}
    @Override public void close() {
        if(!Platform.isFxApplicationThread())throw new IllegalStateException("Close on JavaFX thread");
        if(closed)return;closed=true;layout.stop();reader.shutdownNow();
        long browserProcess=Integer.toUnsignedLong(api.qf_browser_process(id));
        var process=browserProcess>0?ProcessHandle.of(browserProcess):java.util.Optional.<ProcessHandle>empty();
        api.qf_close(id);
        CompletableFuture.runAsync(()->{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            try{
                while(api.qf_exists(id)!=0||process.map(ProcessHandle::isAlive).orElse(false)){
                    if(System.nanoTime()>deadline)throw new IOException("Browser did not exit before cleanup deadline");
                    Thread.sleep(100);
                }
                closeCompletion.complete(null);
            }catch(Exception failure){closeCompletion.completeExceptionally(failure);}
        });
        evaluations.values().forEach(future->future.completeExceptionally(new IllegalStateException("Browser closed")));evaluations.clear();
    }
}
