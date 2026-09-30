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

    CanvasEditorBridge(QuestionContent initial,ContentEditSession session,Consumer<String> errors,Runnable chooseImage) {
        this.initial=Objects.requireNonNull(initial);this.session=session;this.errors=errors;
        host=new CanvasEditorImageHost(chooseImage,this::initializePage);web.setId("canvas-editor-webview");web.setContextMenuEnabled(false);
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
        engine.load(page.editorUrl());
    }
    private void initializePage() {
        if (closed || api != null) return;
        try {
            var candidate=web.getEngine().executeScript("window.canvasEditor");
            if (!(candidate instanceof JSObject editorApi)) return; // Vite's module graph is still loading.
            if (!Boolean.TRUE.equals(editorApi.call("ready"))) {
                var fault=String.valueOf(editorApi.call("error"));
                if (!fault.isBlank()) errors.accept("Canvas Editor 初始化失败："+fault);
                return;
            }
            api=editorApi;
            loadContent(initial);
        } catch (RuntimeException failed) {api=null;errors.accept("Canvas Editor 初始化失败："+failed.getMessage());}
    }
    WebView view(){return web;}
    boolean ready(){return api!=null && !closed;}
    void loadContent(QuestionContent content){initial=Objects.requireNonNull(content);requireReady();api.call("load",CanvasEditorAdapter.toCanvasJson(content,imageData()));}
    QuestionContent getContent(){requireReady();return CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),resourceIds());}
    void setMode(String mode){requireReady();if(!Set.of("edit","readonly").contains(mode))throw new IllegalArgumentException("Invalid mode");api.call("mode",mode);}
    void insertImage(QBankResource resource){requireReady();api.call("insertImage",CanvasEditorAdapter.imageJson(resource,imageData()));}
    void destroy(){if(closed)return;closed=true;if(api!=null)try{api.call("destroy");}finally{api=null;}web.getEngine().loadContent("");}
    private void requireReady(){if(!ready())throw new IllegalStateException("Canvas Editor 尚未就绪");}
    private Set<String> resourceIds(){var ids=new HashSet<String>();session.resources().forEach(r->ids.add(r.id()));return ids;}
    private Map<String,CanvasEditorAdapter.ImageData> imageData(){
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
