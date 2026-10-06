package io.quizforge.desktop.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** Renders only one sandboxed type page. No workspace, database or application object exists here. */
public final class ExtensionPageWorker {
    private final ObjectMapper json = new ObjectMapper();
    private final BufferedWriter output = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
    private final Port port = new Port(); // WebKit keeps weak references to Java bridges.
    private final AtomicBoolean pulseQueued = new AtomicBoolean();
    private WebView web;
    private StackPane root;
    private Scene scene;
    private javafx.embed.swing.JFXPanel panel;
    private boolean initialized, loaded;
    private final CompletableFuture<Void> boot = new CompletableFuture<>();
    private int width = 720, height = 600, offset;
    private String lastImage;

    public static void main(String[] args) throws Exception {
        ExtensionWorkerProcess.watchParent(Long.parseLong(args[0]));
        var worker = new ExtensionPageWorker();
        var ready = new CompletableFuture<Void>();
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); ready.join();
        // JFXPanel owns an embedded JavaFX window: it renders without showing or activating an OS window.
        javax.swing.SwingUtilities.invokeAndWait(()->{worker.panel=new javafx.embed.swing.JFXPanel();worker.panel.setSize(720,600);});
        var clock = Executors.newSingleThreadScheduledExecutor(r -> { var t = new Thread(r,"qf-page-pulse"); t.setDaemon(true); return t; });
        clock.scheduleAtFixedRate(() -> {
            if (!worker.pulseQueued.compareAndSet(false, true)) return;
            Platform.runLater(() -> {
                try {
                    if (worker.loaded) { worker.web.getEngine().executeScript("void 0"); worker.emit(Map.of("kind", "pulse")); worker.capture(); }
                } catch (Throwable failure) { worker.fail(); }
                finally { worker.pulseQueued.set(false); }
            });
        }, 0, 200, TimeUnit.MILLISECONDS);
        try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = ExtensionRuleProtocol.readLine(input)) != null) {
                Map<String,Object> command = worker.json.readValue(line, new TypeReference<>() {});
                var done = new CompletableFuture<Void>();
                Platform.runLater(() -> {
                    try { worker.command(command); done.complete(null); }
                    catch (Throwable failure) { worker.fail(); done.completeExceptionally(failure); }
                });
                done.join();
                if ("initialize".equals(command.get("command"))) worker.boot.join();
            }
        } finally { Runtime.getRuntime().halt(0); }
    }
    private void initialize(Map<String,Object> command) {
        if (initialized) throw new IllegalStateException("Already initialized");
        initialized = true; width = integer(command, "width", 1, 2048);
        web = new WebView(); web.setContextMenuEnabled(false);
        root = new StackPane(web); scene = new Scene(root, width, height, Color.TRANSPARENT);
        panel.setScene(scene);
        web.getEngine().setCreatePopupHandler(features -> null); web.getEngine().setConfirmHandler(message -> false);
        web.getEngine().setOnAlert(event -> {});
        web.getEngine().getLoadWorker().stateProperty().addListener((p,before,state) -> {
            if (loaded && state == Worker.State.SCHEDULED) Runtime.getRuntime().halt(2);
            if (state == Worker.State.SUCCEEDED) {
                try {
                    JSObject window = (JSObject) web.getEngine().executeScript("window");
                    window.setMember("qfPageWorker", port);
                    window.setMember("__qfPageInit", json.writeValueAsString(command));
                    web.getEngine().executeScript("""
                        (()=>{
                          const r=JSON.parse(window.__qfPageInit),frame=document.querySelector('iframe');
                          const session=r.session,port=window.qfPageWorker;
                          delete window.__qfPageInit;
                          window.addEventListener('message',event=>{
                            const m=event.data;
                            if(event.source!==frame.contentWindow||m?.channel!=='qf-type-frame'||m.session!==session)return;
                            if(m.kind==='geometry'&&Number.isFinite(m.height)&&m.height>=0&&m.height<=200000)frame.style.height=Math.max(1,m.height)+'px';
                            port.message(JSON.stringify(m));
                          });
                          window.qfPagePort={deliver:encoded=>frame.contentWindow.postMessage(JSON.parse(encoded),'*'),
                            viewport:(width,height,offset)=>{frame.style.width=width+'px';frame.style.top=-offset+'px';}};
                          frame.srcdoc=r.document;
                        })()
                        """);
                    loaded = true; emit(Map.of("kind", "pulse")); resize(width, height, 0); boot.complete(null);
                } catch (Throwable failure) { fail(); }
            } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) fail();
        });
        // The extension can reach only its opaque sandbox. The Java port is in the trusted parent.
        web.getEngine().loadContent("<!doctype html><meta charset=utf-8><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none';script-src 'nonce-qf-isolated-page-v1';style-src 'unsafe-inline';img-src data:;frame-src 'self' about:;connect-src 'none';object-src 'none';form-action 'none';base-uri 'none'\"><meta http-equiv=\"Content-Security-Policy\" content=\"script-src 'unsafe-inline'\"><style>html,body{margin:0;overflow:hidden;background:transparent}iframe{position:absolute;left:0;top:0;border:0;width:100%;height:600px}</style><iframe sandbox=\"allow-scripts\" referrerpolicy=\"no-referrer\"></iframe>");
    }
    private void command(Map<String,Object> value) {
        String command = Objects.toString(value.get("command"), "");
        if (command.equals("initialize")) { initialize(value); return; }
        if (!loaded) throw new IllegalStateException("Page not ready");
        switch (command) {
            case "send" -> {
                JSObject window = (JSObject)web.getEngine().executeScript("window");
                try { window.setMember("__qfPageMessage", json.writeValueAsString(value.get("message"))); web.getEngine().executeScript("window.qfPagePort.deliver(window.__qfPageMessage)"); }
                catch (IOException failure) { throw new IllegalArgumentException(failure); }
                finally { window.removeMember("__qfPageMessage"); }
            }
            case "viewport" -> resize(integer(value,"width",1,2048),integer(value,"height",1,1536),integer(value,"offset",0,200000));
            case "input" -> input(object(value.get("event")));
            default -> throw new IllegalArgumentException("Unknown page command");
        }
    }
    private void resize(int nextWidth,int nextHeight,int nextOffset) {
        if (width == nextWidth && height == nextHeight && offset == nextOffset && lastImage != null) return;
        width=nextWidth;height=nextHeight;offset=nextOffset;lastImage=null;
        javax.swing.SwingUtilities.invokeLater(()->panel.setSize(nextWidth,nextHeight));
        root.resize(width,height);root.applyCss();root.layout();web.resize(width,height);
        web.getEngine().executeScript("window.qfPagePort.viewport("+width+","+height+","+offset+")");
    }
    private void input(Map<String,Object> value) {
        String type=Objects.toString(value.get("type"),"");
        boolean shift=Boolean.TRUE.equals(value.get("shiftKey")),ctrl=Boolean.TRUE.equals(value.get("ctrlKey")),alt=Boolean.TRUE.equals(value.get("altKey")),meta=Boolean.TRUE.equals(value.get("metaKey"));
        if (type.startsWith("pointer")) {
            double x=number(value,"x"),y=number(value,"y")-offset;
            MouseButton button=switch(((Number)value.getOrDefault("button",0)).intValue()){case 1->MouseButton.MIDDLE;case 2->MouseButton.SECONDARY;default->MouseButton.PRIMARY;};
            var eventType=switch(type){case "pointerdown"->MouseEvent.MOUSE_PRESSED;case "pointerup"->MouseEvent.MOUSE_RELEASED;case "pointermove"->MouseEvent.MOUSE_MOVED;case "pointerdrag"->MouseEvent.MOUSE_DRAGGED;default->throw new IllegalArgumentException("Invalid pointer event");};
            int buttons=((Number)value.getOrDefault("buttons",0)).intValue();
            if(type.equals("pointerdown"))web.requestFocus();
            web.fireEvent(new MouseEvent(eventType,x,y,x,y,button,((Number)value.getOrDefault("clickCount",1)).intValue(),shift,ctrl,alt,meta,(buttons&1)!=0,(buttons&4)!=0,(buttons&2)!=0,false,false,true,new PickResult(web,x,y)));
        } else if (type.equals("keydown")||type.equals("keyup")) {
            KeyCode code=keyCode(Objects.toString(value.get("code"),""));
            web.fireEvent(new KeyEvent(type.equals("keydown")?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,KeyEvent.CHAR_UNDEFINED,Objects.toString(value.get("key"),""),code,shift,ctrl,alt,meta));
        } else if (type.equals("text")) {
            String text=Objects.toString(value.get("text"),"");if(text.length()>65536)throw new IllegalArgumentException("Input too large");
            for(int start=0;start<text.length();) {int end=start+Character.charCount(text.codePointAt(start));web.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED,text.substring(start,end),"",KeyCode.UNDEFINED,false,false,false,false));start=end;}
        } else if (type.equals("blur")) {
            web.getEngine().executeScript("window.qfPagePort.deliver(JSON.stringify({channel:'qf-type-frame',session:"+quote(value.get("session"))+",kind:'blur'}))");
        } else throw new IllegalArgumentException("Invalid input event");
    }
    private static KeyCode keyCode(String code) {
        if(code.matches("Key[A-Z]"))return KeyCode.valueOf(code.substring(3));
        if(code.matches("Digit[0-9]"))return KeyCode.valueOf("DIGIT"+code.substring(5));
        String name=switch(code){case "ArrowLeft"->"LEFT";case "ArrowRight"->"RIGHT";case "ArrowUp"->"UP";case "ArrowDown"->"DOWN";case "Backspace"->"BACK_SPACE";case "Space"->"SPACE";case "ControlLeft","ControlRight"->"CONTROL";case "ShiftLeft","ShiftRight"->"SHIFT";case "AltLeft","AltRight"->"ALT";case "MetaLeft","MetaRight"->"META";default->code.toUpperCase(Locale.ROOT);};
        try{return KeyCode.valueOf(name);}catch(IllegalArgumentException unknown){return KeyCode.UNDEFINED;}
    }
    private void capture() throws IOException {
        root.applyCss();root.layout();
        var parameters=new SnapshotParameters();parameters.setFill(Color.TRANSPARENT);
        var image=web.snapshot(parameters,null);
        int w=(int)image.getWidth(),h=(int)image.getHeight();
        if(w<1||h<1||w>2048||h>1536)throw new IOException("Invalid snapshot bounds");
        int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);
        var png=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,pixels,0,w);
        var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(png,"png",bytes);
        String encoded=Base64.getEncoder().encodeToString(bytes.toByteArray());
        if(encoded.equals(lastImage))return;lastImage=encoded;
        emit(Map.of("kind","image","png",encoded,"width",w,"height",h,"offset",offset));
    }
    public final class Port {
        public void message(String encoded) {
            try {
                if(encoded==null||encoded.length()>1024*1024)throw new IOException("Page message too large");
                emit(Map.of("kind","page","message",json.readValue(encoded,new TypeReference<Map<String,Object>>() {})));
            } catch (IOException failure) { fail(); }
        }
    }
    private synchronized void emit(Map<String,Object> value) throws IOException {
        String encoded=json.writeValueAsString(value);if(encoded.length()>ExtensionRuleProtocol.MAX_LINE)throw new IOException("Page response too large");
        output.write(encoded);output.newLine();output.flush();
    }
    private void fail(){try{emit(Map.of("kind","failure","code","EXTENSION_FAILED","message","题型页面渲染失败，请重新加载。"));}catch(IOException ignored){}Runtime.getRuntime().halt(2);}
    private String quote(Object value){try{return json.writeValueAsString(value);}catch(IOException e){throw new IllegalArgumentException(e);}}
    private static Map<String,Object> object(Object value){if(!(value instanceof Map<?,?> raw))throw new IllegalArgumentException("Expected object");@SuppressWarnings("unchecked")var result=(Map<String,Object>)raw;return result;}
    private static int integer(Map<String,Object> value,String name,int min,int max){double number=number(value,name);if(number!=Math.rint(number)||number<min||number>max)throw new IllegalArgumentException("Invalid "+name);return (int)number;}
    private static double number(Map<String,Object> value,String name){if(!(value.get(name) instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("Invalid "+name);return n.doubleValue();}
}
