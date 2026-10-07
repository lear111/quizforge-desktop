package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.desktop.ui.content.QuestionContentLayout;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayDeque;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Uses the same Canvas renderer as editing, without tools or a second layout conversion. */
public final class CanvasDocumentView extends StackPane {
    private static final Object LIVE_PREVIEWS = new Object();
    private static final int MAX_CACHED_PREVIEWS = 6;
    private CanvasEditorBridge bridge;
    private final QuestionContent content;
    private final List<QBankResource> resources;
    private final QuestionResourceInput input;
    private boolean released=true;
    public CanvasDocumentView(DocumentContent content,List<QBankResource> resources,QuestionResourceInput input,String prefix) {
        this((QuestionContent)content,resources,input,prefix);
    }
    public CanvasDocumentView(QuestionContent content,List<QBankResource> resources,QuestionResourceInput input,String prefix) {
        setId(prefix+"native-document");setMinWidth(0);setMaxWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        setMinHeight(USE_PREF_SIZE);setMaxHeight(USE_PREF_SIZE);
        this.content=content;this.resources=List.copyOf(resources);this.input=input;
        // A cache hit must not allocate a second WebView just to discard it.
        // Inactive tabs also stay lightweight until their first Scene attachment.
        sceneProperty().addListener((o,old,current)->{
            if(current!=null){
                reuseDetachedPreview(current);
                if(released)createBridge();
                pool(current).live.add(this);
            }
            if(old!=null && current==null){
                var detached=bridge;
                Platform.runLater(()->{
                    if(getScene()!=old)pool(old).live.remove(this);
                    // A synchronous page rebuild may already have transferred
                    // this renderer to the replacement preview.
                    if(getScene()==null && !released && bridge==detached){
                        if(bridge.ready() && getChildren().contains(bridge.view())
                                && old.getWindow()!=null && old.getWindow().isShowing()){
                            bridge.parkPreview();
                            pool(old).cache(new CachedPreview(key(),bridge));
                            bridge=null;getChildren().clear();
                        }else{bridge.destroy();bridge=null;getChildren().clear();}
                        released=true;
                    }
                });
            }
        });
    }
    private static PreviewPool pool(Scene scene){
        return (PreviewPool)scene.getProperties().computeIfAbsent(LIVE_PREVIEWS,ignored->new PreviewPool(scene));
    }
    private record PreviewKey(String id,QuestionContent content,List<QBankResource> resources){}
    private record CachedPreview(PreviewKey key,CanvasEditorBridge bridge){}
    /** Only detached, ready renderers are cached. Old views and answer callbacks are released. */
    private static final class PreviewPool {
        final Set<CanvasDocumentView> live=new LinkedHashSet<>();
        final ArrayDeque<CachedPreview> cached=new ArrayDeque<>();
        final javafx.beans.value.ChangeListener<Boolean> showing=(o,before,current)->{if(!current)clear();};
        PreviewPool(Scene scene){
            scene.windowProperty().addListener((o,before,current)->{
                if(before!=null){before.showingProperty().removeListener(showing);clear();}
                if(current!=null)current.showingProperty().addListener(showing);
            });
            if(scene.getWindow()!=null)scene.getWindow().showingProperty().addListener(showing);
        }
        void cache(CachedPreview entry){
            for(var iterator=cached.iterator();iterator.hasNext();){
                var previous=iterator.next();
                if(previous.key.equals(entry.key)){iterator.remove();previous.bridge.destroy();}
            }
            cached.addLast(entry);
            while(cached.size()>MAX_CACHED_PREVIEWS)cached.removeFirst().bridge.destroy();
        }
        CanvasEditorBridge take(PreviewKey key){
            for(var iterator=cached.iterator();iterator.hasNext();){
                var entry=iterator.next();
                if(entry.key.equals(key)){iterator.remove();return entry.bridge;}
            }
            return null;
        }
        void clear(){while(!cached.isEmpty())cached.removeFirst().bridge.destroy();}
    }
    private List<QBankResource> usedResources(){
        var ids=QuestionContentData.resourceIds(content);
        return resources.stream().filter(resource->ids.contains(resource.id())).toList();
    }
    private PreviewKey key(){return new PreviewKey(getId(),content,usedResources());}
    private void reuseDetachedPreview(Scene scene){
        for(var previous:List.copyOf(pool(scene).live)){
            if(previous==this || previous.getScene()!=null || previous.released || previous.bridge==null
                    || !previous.getChildren().contains(previous.bridge.view()))continue;
            if(!key().equals(previous.key()))continue;
            if(bridge!=null)bridge.destroy();
            bridge=previous.bridge;previous.bridge=null;previous.released=true;
            previous.getChildren().clear();pool(scene).live.remove(previous);
            attachReusedBridge();
            return;
        }
        var cached=pool(scene).take(key());
        if(cached!=null){if(bridge!=null)bridge.destroy();bridge=cached;attachReusedBridge();}
    }
    private void attachReusedBridge(){
        released=false;
        bridge.rebindPreview(new ContentEditSession(content,resources,input),this::showError);
        getChildren().setAll(bridge.view());
    }
    private void showError(String message){
        var error=new Label("富文本文档无法显示："+message);error.setWrapText(true);getChildren().setAll(error);
    }
    private void createBridge() {
        released=false;
        bridge=new CanvasEditorBridge(content,new ContentEditSession(content,resources,input),this::showError,()->{},true);
        bridge.view().setMinWidth(0);bridge.view().setPrefWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        bridge.view().setPrefHeight(80);getChildren().setAll(bridge.view());
    }
}
