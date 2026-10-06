package io.quizforge.desktop.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import io.quizforge.infrastructure.json.DocumentJson;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** Trusted local-shell bridge. Sandboxed extensions receive only the existing QF message API. */
public final class ExtensionPageBridge implements AutoCloseable {
    private static final Map<javafx.scene.web.WebEngine,ExtensionPageBridge> HOSTS = new WeakHashMap<>();
    private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();
    private final WeakReference<WebView> view;
    private final Map<String,Slot> pages = new HashMap<>();
    private boolean closed;
    private final javafx.beans.value.ChangeListener<Worker.State> loadListener;
    private ExtensionPageBridge(WebView web) {
        view=new WeakReference<>(web);
        loadListener=(o,before,after)->{if(after==Worker.State.SCHEDULED||after==Worker.State.CANCELLED||after==Worker.State.FAILED)close();};
        web.getEngine().getLoadWorker().stateProperty().addListener(loadListener);
    }
    public static void install(WebView web) {
        requireFx();var previous=HOSTS.remove(web.getEngine());if(previous!=null)previous.close();
        var bridge=new ExtensionPageBridge(web);HOSTS.put(web.getEngine(),bridge);
        CLEANER.register(web,()->{try{Platform.runLater(bridge::close);}catch(IllegalStateException shutdown){/* Parent monitor stops workers when the application exits. */}});
        ((JSObject)web.getEngine().executeScript("window")).setMember("nativePagesHost",bridge);
    }
    public static void closeAll(){requireFx();List.copyOf(HOSTS.values()).forEach(ExtensionPageBridge::close);HOSTS.clear();}
    public void open(String session,String document,int width) {
        requireFx();if(closed)return;
        if(session==null||!session.matches("[a-zA-Z0-9-]{8,128}")||document==null||document.length()>8*1024*1024||width<1||width>2048||pages.size()>=8||pages.containsKey(session))
            throw new IllegalArgumentException("Invalid isolated page initialization");
        var slot=new Slot();pages.put(session,slot);
        slot.runtime=new ExtensionPageRuntime(document,session,width,event->accept(session,slot,event));
    }
    public void send(String session,String encoded){sendCommand(session,"send","message",encoded);}
    public void input(String session,String encoded){sendCommand(session,"input","event",encoded);}
    private void sendCommand(String session,String command,String field,String encoded){
        requireFx();var slot=pages.get(session);if(closed||slot==null)return;
        try {
            if(encoded==null||encoded.length()>8*1024*1024)throw new IllegalArgumentException("Page message too large");
            var value=DocumentJson.mapper().readValue(encoded,new TypeReference<Map<String,Object>>() {});
            slot.runtime.send(Map.of("command",command,field,value));
        }catch(java.io.IOException invalid){throw new IllegalArgumentException("Invalid page message",invalid);}
    }
    public void viewport(String session,int width,int height,int offset){
        requireFx();var slot=pages.get(session);if(closed||slot==null)return;
        if(width<1||width>2048||height<1||height>1536||offset<0||offset>200000)throw new IllegalArgumentException("Invalid page viewport");
        slot.runtime.send(Map.of("command","viewport","width",width,"height",height,"offset",offset));
    }
    /** Native ScrollPane clipping is outside DOM layout; report only visible local-shell coordinates. */
    public String visibleBounds(){
        requireFx();WebView web=view.get();
        if(web==null||web.getScene()==null)return "{\"top\":0,\"height\":900}";
        var origin=web.localToScene(0,0);double top=Math.max(0,-origin.getY());
        return "{\"top\":"+top+",\"height\":"+Math.max(1,web.getScene().getHeight())+"}";
    }
    public void closePage(String session){requireFx();var slot=pages.remove(session);if(slot!=null)slot.runtime.close();}
    private void accept(String session,Slot slot,Map<String,Object> event){
        // Coalesce images; a slow application UI must not accumulate unlimited PNG callbacks.
        if("image".equals(event.get("kind"))){slot.image=event;if(!slot.imageQueued.compareAndSet(false,true))return;
            Platform.runLater(()->{var next=slot.image;slot.imageQueued.set(false);deliver(session,slot,next);});
        }else Platform.runLater(()->deliver(session,slot,event));
    }
    private void deliver(String session,Slot slot,Map<String,Object> event){
        requireFx();if(closed||pages.get(session)!=slot)return;
        WebView web=view.get();if(web==null){close();return;}
        try{
            var window=(JSObject)web.getEngine().executeScript("window");
            window.setMember("__qfPageEvent",DocumentJson.mapper().writeValueAsString(Map.of("session",session,"event",event)));
            try{web.getEngine().executeScript("window.qfNativePages?.receiveFromJson(window.__qfPageEvent)");}
            finally{window.removeMember("__qfPageEvent");}
        }catch(Exception unavailable){closePage(session);}
    }
    @Override public void close(){
        requireFx();if(closed)return;closed=true;pages.values().forEach(slot->slot.runtime.close());pages.clear();
        var web=view.get();if(web!=null)web.getEngine().getLoadWorker().stateProperty().removeListener(loadListener);
    }
    private static void requireFx(){if(!Platform.isFxApplicationThread())throw new IllegalStateException("Use JavaFX thread");}
    private static final class Slot{ExtensionPageRuntime runtime;volatile Map<String,Object> image;final AtomicBoolean imageQueued=new AtomicBoolean();}
}
