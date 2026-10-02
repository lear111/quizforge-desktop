package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
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
    private ContentEditSession session;
    private Consumer<String> errors;
    private final CanvasEditorImageHost host;
    private QuestionContent initial;
    private JSObject api;
    private boolean closed;
    private boolean loading;
    private boolean pageStarted;
    private String savedValue;
    private final boolean preview;
    private Map<String,Object> cloze;
    private boolean translation;

    CanvasEditorBridge(QuestionContent initial,ContentEditSession session,Consumer<String> errors,Runnable chooseImage) {
        this(initial,session,errors,chooseImage,false);
    }
    CanvasEditorBridge(QuestionContent initial,ContentEditSession session,Consumer<String> errors,Runnable chooseImage,boolean preview) {
        this.preview=preview;
        if(preview){web.setPageFill(javafx.scene.paint.Color.TRANSPARENT);web.setOpacity(0);}
        this.initial=Objects.requireNonNull(initial);this.session=session;this.errors=errors;
        host=new CanvasEditorImageHost(chooseImage,this::initializePage,message->this.errors.accept(message));web.setId("canvas-editor-webview");web.setContextMenuEnabled(false);
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
                engine.getLoadWorker().cancel();this.errors.accept("Canvas Editor 禁止外部导航");
            }
        });
        engine.getLoadWorker().stateProperty().addListener((o,a,b)->{
            if(b==Worker.State.FAILED && !closed)this.errors.accept("Canvas Editor 加载失败："+engine.getLoadWorker().getException());
            if(b==Worker.State.SUCCEEDED && !closed)try {
                ((JSObject)engine.executeScript("window")).setMember("quizforgeHost",host);
                initializePage();
            }catch(RuntimeException failed){api=null;this.errors.accept("Canvas Editor 初始化失败："+failed.getMessage());}
        });
        if(preview)web.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL,event->{
            // A bounded cloze menu scrolls independently; the document still scrolls with its parent.
            if(cloze!=null && ready() && Boolean.TRUE.equals(engine.executeScript(
                    "!!document.elementFromPoint("+event.getX()+","+event.getY()+")?.closest('#cloze-options-popup')")))return;
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
        if(preview){
            // Do not start an unused replacement's page before its owner can
            // adopt a renderer detached by this same JavaFX update.
            web.sceneProperty().addListener((o,before,current)->{
                if(current!=null)javafx.application.Platform.runLater(()->{
                    if(!closed && web.getScene()!=null && !pageStarted){pageStarted=true;engine.load(page.previewUrl());}
                });
            });
        }else engine.load(page.editorUrl());
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
    void rebindPreview(ContentEditSession current,Consumer<String> listener){
        if(!preview)throw new IllegalStateException("Only readonly previews can change owners");
        session=Objects.requireNonNull(current);errors=Objects.requireNonNull(listener);
    }
    void parkPreview(){
        if(!preview || !ready())throw new IllegalStateException("Only loaded readonly previews can be cached");
        host.onCloze(null);errors=ignored->{};session=null;
        web.getEngine().executeScript("document.getElementById('cloze-options-popup')?.remove()");
    }
    boolean ready(){return api!=null && !closed;}
    void onContentChanged(Runnable listener){host.onChange(()->{if(ready() && !loading && !preview)listener.run();});}
    boolean hasChanges(){return ready() && savedValue!=null && !savedValue.equals(call("value"));}
    void checkpoint(){savedValue=(String)call("value");}
    void loadContent(QuestionContent content){
        initial=Objects.requireNonNull(content);requireReady();
        loading=true;
        try{
            if(content instanceof DocumentContent document)call("loadDocument",session.document(document));
            else call("load",CanvasEditorAdapter.toCanvasJson(content,imageData(session,QuestionContentData.resourceIds(content))));
            if(preview)call("mode","readonly");
            if(cloze!=null)call("cloze",ContentJson.write(cloze));
            if(translation)call("translation");
            // Readonly previews never need a second serialized document for dirty tracking.
            if(!preview)checkpoint();
            if(preview)web.setOpacity(1);
        }finally{loading=false;}
    }
    void cloze(Map<String,Object> configuration,java.util.function.BiConsumer<Integer,String> choose){
        boolean changed=!Objects.equals(cloze,configuration);
        cloze=configuration;host.onCloze(choose);
        if(changed && ready())call("cloze",ContentJson.write(cloze));
    }
    void translation(){translation=true;if(ready())call("translation");}
    QuestionContent getContent(){
        requireReady();
        if(Boolean.TRUE.equals(call("empty")))return new TextContent("");
        return session.stageDocument((String)call("document"),(String)call("text"));
    }
    void setMode(String mode){requireReady();if(!Set.of("edit","readonly").contains(mode))throw new IllegalArgumentException("Invalid mode");call("mode",mode);}
    void insertImage(QBankResource resource){requireReady();call("insertImage",CanvasEditorAdapter.imageJson(resource,imageData(session,Set.of(resource.id()))));}
    void destroy(){
        if(closed)return;
        try{if(api!=null)call("destroy");}
        finally{
            closed=true;api=null;session=null;initial=null;cloze=null;savedValue=null;
            host.onCloze(null);host.onChange(null);errors=ignored->{};
            web.getEngine().loadContent("");
        }
    }
    private void requireReady(){if(!ready())throw new IllegalStateException("Canvas Editor 尚未就绪");}
    private Object call(String method,Object... args) {
        requireReady();
        // Resolve the live global API inside JavaScript, not a cached native function handle.
        String name=ContentJson.write(method);
        String arguments=ContentJson.write(Arrays.asList(args)).replace("\u2028","\\u2028").replace("\u2029","\\u2029");
        return web.getEngine().executeScript("window.canvasEditor["+name+"].apply(window.canvasEditor,"+arguments+")");
    }
    static Map<String,CanvasEditorAdapter.ImageData> imageData(ContentEditSession session){
        return imageData(session,null);
    }
    static Map<String,CanvasEditorAdapter.ImageData> imageData(ContentEditSession session,Set<String> required){
        var result=new HashMap<String,CanvasEditorAdapter.ImageData>();
        for(var resource:session.resources())if(resource.kind()==ResourceKind.IMAGE && (required==null || required.contains(resource.id())))try(var in=session.open(resource)){
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
