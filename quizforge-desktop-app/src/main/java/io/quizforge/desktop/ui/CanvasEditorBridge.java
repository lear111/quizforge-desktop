package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;
import io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import javafx.concurrent.Worker;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** Narrow, local-only Java/WebView bridge for the Canvas Editor POC. */
final class CanvasEditorBridge {
    private final WebView web=new WebView();
    private final ContentEditSession session;
    private final Consumer<String> errors;
    private final CanvasEditorImageHost host;
    private QuestionContent initial;
    private JSObject api;
    private boolean closed;
    private final boolean preview;

    CanvasEditorBridge(QuestionContent initial,ContentEditSession session,Consumer<String> errors,Runnable chooseImage) {
        this(initial,session,errors,chooseImage,false);
    }
    CanvasEditorBridge(QuestionContent initial,ContentEditSession session,Consumer<String> errors,Runnable chooseImage,boolean preview) {
        this.preview=preview;
        this.initial=Objects.requireNonNull(initial);this.session=session;this.errors=errors;
        host=new CanvasEditorImageHost(chooseImage,this::initializePage,errors);web.setId("canvas-editor-webview");web.setContextMenuEnabled(false);
        var page=new CanvasEditorPageLocator();
        var engine=web.getEngine();engine.setCreatePopupHandler(ignored->null);
        engine.setConfirmHandler(ignored->false);
        engine.setPromptHandler(prompt->{
            var dialog=new TextInputDialog(prompt.getDefaultValue());
            if(web.getScene()!=null && web.getScene().getWindow()!=null)dialog.initOwner(web.getScene().getWindow());
            dialog.setTitle("Canvas Editor 输入");dialog.setHeaderText(prompt.getMessage());
            return dialog.showAndWait().orElse(null);
        });
        engine.locationProperty().addListener((o,a,b)->{
            if(b!=null && !b.isEmpty() && !"about:blank".equals(b) && !page.isEditorUrl(b)){
                engine.getLoadWorker().cancel();errors.accept("Canvas Editor 禁止外部导航");
            }
        });
        engine.getLoadWorker().stateProperty().addListener((o,a,b)->{
            if(b==Worker.State.FAILED)errors.accept("Canvas Editor 加载失败："+engine.getLoadWorker().getException());
            if(b==Worker.State.SUCCEEDED && !closed)try {
                ((JSObject)engine.executeScript("window")).setMember("quizforgeHost",host);
                initializePage();
            }catch(RuntimeException failed){api=null;errors.accept("Canvas Editor 初始化失败："+failed.getMessage());}
        });
        if(preview)web.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL,event->{
            // Preview is part of the surrounding page, not an independent viewport.
            if(!event.isControlDown() && web.getParent()!=null)
                javafx.event.Event.fireEvent(web.getParent(),event.copyFor(web.getParent(),web.getParent()));
            event.consume();
        });
        if(preview)host.onHeight(height->{
            if(closed)return;
            double contentHeight=Math.max(24,Math.ceil(height));
            web.setMinHeight(contentHeight);web.setPrefHeight(contentHeight);web.setMaxHeight(contentHeight);
        });
        engine.load(preview?page.previewUrl():page.editorUrl());
    }
    private void initializePage() {
        if (closed || api != null) return;
        try {
            var candidate=web.getEngine().executeScript("window.canvasEditor");
            if (!(candidate instanceof JSObject editorApi)) return; // Vite's module graph is still loading.
            if (!Boolean.TRUE.equals(web.getEngine().executeScript("window.canvasEditor.ready()"))) {
                var fault=String.valueOf(web.getEngine().executeScript("window.canvasEditor.error()"));
                if (!fault.isBlank()) errors.accept("Canvas Editor 初始化失败："+fault);
                return;
            }
            api=editorApi;
            loadContent(initial);
        } catch (RuntimeException failed) {api=null;errors.accept("Canvas Editor 初始化失败："+failed.getMessage());}
    }
    WebView view(){return web;}
    boolean ready(){return api!=null && !closed;}
    void loadContent(QuestionContent content){
        initial=Objects.requireNonNull(content);requireReady();
        if(content instanceof DocumentContent document)call("loadDocument",session.document(document));
        else call("load",CanvasEditorAdapter.toCanvasJson(content,imageData()));
        if(preview)call("mode","readonly");
    }
    QuestionContent getContent(){
        requireReady();
        if(Boolean.TRUE.equals(call("empty")))return new TextContent("");
        return session.stageDocument((String)call("document"),(String)call("text"));
    }
    void setMode(String mode){requireReady();if(!Set.of("edit","readonly").contains(mode))throw new IllegalArgumentException("Invalid mode");call("mode",mode);}
    void insertImage(QBankResource resource){requireReady();call("insertImage",CanvasEditorAdapter.imageJson(resource,imageData()));}
    void destroy(){if(closed)return;try{if(api!=null)call("destroy");}finally{closed=true;api=null;web.getEngine().loadContent("");}}
    private void requireReady(){if(!ready())throw new IllegalStateException("Canvas Editor 尚未就绪");}
    private Object call(String method,Object... args) {
        requireReady();
        // Resolve the live global API inside JavaScript, not a cached native function handle.
        String name=RichContentEditorAdapter.json(method);
        String arguments=RichContentEditorAdapter.json(Arrays.asList(args)).replace("\u2028","\\u2028").replace("\u2029","\\u2029");
        return web.getEngine().executeScript("window.canvasEditor["+name+"].apply(window.canvasEditor,"+arguments+")");
    }
    private Map<String,CanvasEditorAdapter.ImageData> imageData(){
        return imageData(session);
    }
    static Map<String,CanvasEditorAdapter.ImageData> imageData(ContentEditSession session){
        var result=new HashMap<String,CanvasEditorAdapter.ImageData>();
        for(var resource:session.resources())if(resource.kind()==ResourceKind.IMAGE)try(var in=session.open(resource)){
            if(in==null)continue;byte[] bytes=in.readNBytes((int)QBankImageImporter.MAX_BYTES+1);
            if(bytes.length>QBankImageImporter.MAX_BYTES)continue;
            if(!Set.of("image/png","image/jpeg").contains(resource.mediaType()))continue;
            var picture=new Image(new java.io.ByteArrayInputStream(bytes));
            if(picture.isError() || picture.getWidth()<=0 || picture.getHeight()<=0)continue;
            String url="data:"+resource.mediaType()+";base64,"+Base64.getEncoder().encodeToString(bytes);
            result.put(resource.id(),new CanvasEditorAdapter.ImageData(url,(int)picture.getWidth(),(int)picture.getHeight()));
        }catch(IOException | RuntimeException missing){/* Adapter rejects an unresolved image on save/load. */}
        return result;
    }
}
