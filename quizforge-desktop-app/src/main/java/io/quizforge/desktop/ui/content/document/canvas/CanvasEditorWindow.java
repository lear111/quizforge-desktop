package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.desktop.ui.content.ContentEditResult;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Native Canvas document editor; Save stages the complete document as a package resource. */
public final class CanvasEditorWindow {
    private final Stage stage=new Stage();
    private final ContentEditSession session;
    private final CanvasEditorBridge bridge;
    private final Label error=new Label();
    private ContentEditResult result=ContentEditResult.cancelled();
    private final javafx.animation.PauseTransition draftDelay=new javafx.animation.PauseTransition(javafx.util.Duration.millis(450));
    private java.util.function.Consumer<ContentEditResult> draftSaver;
    private boolean draftDirty;

    CanvasEditorWindow(Window owner,String title,QuestionContent content,List<QBankResource> resources,QuestionResourceInput input) {
        session=new ContentEditSession(content,resources,input);
        stage.setTitle(title);if(owner!=null){stage.initOwner(owner);stage.initModality(Modality.WINDOW_MODAL);}
        stage.setWidth(1180);stage.setHeight(790);stage.setMinWidth(875);stage.setMinHeight(560);
        bridge=new CanvasEditorBridge(content,session,this::showError,this::chooseImage);
        stage.getProperties().put("quizforge.canvas.window",this);
        var back=new Button("← 返回");back.setId("canvas-editor-back");back.setOnAction(e->cancel());
        var heading=new Label(title);heading.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#333b45;");
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var save=new Button("保存");save.setId("canvas-editor-save");save.setOnAction(e->save());
        error.setStyle("-fx-text-fill:#b44a44;-fx-font-size:12px;");
        var bar=new HBox(12,back,heading,spacer,error,save);bar.setId("canvas-editor-window-bar");
        bar.setAlignment(Pos.CENTER_LEFT);bar.setPadding(new Insets(7,14,7,14));
        bar.setStyle("-fx-background-color:#f8f9fb;-fx-border-color:#dfe3e9;-fx-border-width:0 0 1 0;");
        var root=new BorderPane(bridge.view());root.setTop(bar);root.setId("canvas-editor-window");
        var scene=new Scene(root);UiTheme.apply(scene);stage.setScene(scene);
        draftDelay.setOnFinished(e->persistDraft());
        bridge.onContentChanged(()->{if(draftSaver!=null){draftDirty=true;draftDelay.playFromStart();}});
        stage.setOnCloseRequest(e->{
            if(draftSaver!=null){if(!persistDraft())e.consume();}
            else if(!result.saved())result=session.cancel();
        });
        stage.setOnHidden(e->{
            draftDelay.stop();if(draftSaver!=null && bridge.ready())persistDraft();bridge.destroy();
        });
    }
    public static ContentEditResult openEditor(Window owner,String title,QuestionContent content,
            List<QBankResource> resources,QuestionResourceInput input) {
        var window=new CanvasEditorWindow(owner,title,content,resources,input);
        window.stage.showAndWait();return window.result;
    }
    public static ContentEditResult openEditor(Window owner,String title,QuestionContent content,
            List<QBankResource> resources,QuestionResourceInput input,java.util.function.Consumer<ContentEditResult> draftSaver) {
        var window=new CanvasEditorWindow(owner,title,content,resources,input);
        window.saveDraftsTo(draftSaver);
        window.stage.showAndWait();return window.result;
    }
    CanvasEditorBridge bridge(){return bridge;}
    void saveDraftsTo(java.util.function.Consumer<ContentEditResult> saver){draftSaver=java.util.Objects.requireNonNull(saver);}
    ContentEditSession session(){return session;}
    Stage stage(){return stage;}
    private void chooseImage(){
        var chooser=new FileChooser();chooser.setTitle("插入图片");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG / JPEG 图片","*.png","*.jpg","*.jpeg"));
        var file=chooser.showOpenDialog(stage);if(file!=null)try {
            var resource=session.stage(file.toPath());bridge.insertImage(resource);error.setText("");
        }catch(RuntimeException failed){showError(failed.getMessage());}
    }
    private boolean persistDraft(){
        draftDelay.stop();
        if(!bridge.ready()){
            if(!draftDirty)return true;
            showError("草稿保存失败：编辑器尚未就绪");return false;
        }
        try{
            // Canvas change notifications are asynchronous; inspect the live document
            // when closing so the final keystroke cannot miss the debounce window.
            if(!draftDirty && !bridge.hasChanges())return true;
            var draft=session.save(bridge.getContent());draftSaver.accept(draft);result=draft;draftDirty=false;
            bridge.checkpoint();
            session.retainResources(draft.content());error.setText("草稿已保存");return true;
        }catch(RuntimeException failed){showError("草稿保存失败："+failed.getMessage());return false;}
    }
    private void save(){
        if(draftSaver!=null){if(persistDraft())stage.close();return;}
        try{result=session.save(bridge.getContent());stage.close();}catch(RuntimeException failed){showError("保存失败："+failed.getMessage());}
    }
    private void cancel(){
        if(draftSaver!=null){if(persistDraft())stage.close();return;}
        result=session.cancel();stage.close();
    }
    private void showError(String message){error.setText(message==null?"编辑失败":message);}
}
