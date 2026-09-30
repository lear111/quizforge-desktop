package io.quizforge.desktop.ui;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.*;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/** Modal, unpaged authoring shell around the reusable content editor. */
final class RichContentEditorWindow {
    private final Stage stage=new Stage();
    private final ContentEditSession session;
    private final RichContentEditor editor;
    private final Label errors=new Label();
    private ContentEditResult result=ContentEditResult.cancelled();

    RichContentEditorWindow(Window owner,QuestionContent content,RichContentEditorProfile profile,
            List<QBankResource> resources,QuestionResourceInput input) {
        if(profile!=RichContentEditorProfile.PROMPT)throw new IllegalArgumentException("Unsupported editor profile");
        session=new ContentEditSession(content,resources,input);
        editor=new RichContentEditor(session,content,this::error);
        stage.setTitle("编辑题干");if(owner!=null){stage.initOwner(owner);stage.initModality(Modality.WINDOW_MODAL);}
        stage.setWidth(1100);stage.setHeight(760);stage.setMinWidth(840);stage.setMinHeight(550);
        var toolbar=new FlowPane(6,6);toolbar.setId("rich-editor-toolbar");toolbar.setPadding(new Insets(12,16,12,16));
        command(toolbar,"撤销","undo");command(toolbar,"重做","redo");
        command(toolbar,"B","bold");command(toolbar,"I","italic");command(toolbar,"U","underline");command(toolbar,"S","strike");command(toolbar,"清除格式","clear");
        command(toolbar,"正文","paragraph");command(toolbar,"H1","heading1");command(toolbar,"H2","heading2");command(toolbar,"H3","heading3");
        command(toolbar,"左对齐","left");command(toolbar,"居中","center");command(toolbar,"右对齐","right");
        command(toolbar,"项目符号","bullet");command(toolbar,"编号","ordered");command(toolbar,"引用","quote");
        button(toolbar,"链接","link",()->link());
        button(toolbar,"插入图片","insert-image",()->chooseImage(false));
        button(toolbar,"替换图片","replace-image",()->chooseImage(true));
        button(toolbar,"删除图片","delete-image",()->attempt(editor::deleteSelectedImage));
        for(int width:List.of(25,50,75,100))button(toolbar,width+"%","image-width-"+width,()->attempt(()->editor.setImageWidth(width)));
        for(var alignment:TextAlignment.values())button(toolbar,"图片"+switch(alignment){case LEFT->"左";case CENTER->"中";case RIGHT->"右";},
                "image-align-"+alignment.name().toLowerCase(),()->attempt(()->editor.setImageAlignment(alignment)));
        var canvas=new StackPane(editor);canvas.setId("rich-editor-canvas");canvas.setAlignment(Pos.TOP_CENTER);
        canvas.setPadding(new Insets(30,24,80,24));canvas.setStyle("-fx-background-color:#f5f4f2;");
        var scroll=new ScrollPane(canvas);scroll.setFitToWidth(true);scroll.setPannable(true);scroll.setId("rich-editor-scroll");
        var cancel=new Button("取消");cancel.setId("rich-editor-cancel");cancel.setOnAction(e->cancel());
        var save=new Button("保存并返回");save.setId("rich-editor-save");save.setOnAction(e->save());
        errors.setStyle("-fx-text-fill:#ae463b;");var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var footer=new HBox(12,errors,spacer,cancel,save);footer.setId("rich-editor-footer");
        footer.setPadding(new Insets(12,18,12,18));footer.setAlignment(Pos.CENTER_RIGHT);
        var root=new BorderPane(scroll);root.setTop(toolbar);root.setBottom(footer);root.setId("rich-editor-window");
        var scene=new Scene(root);UiTheme.apply(scene);stage.setScene(scene);
        stage.setOnCloseRequest(e->{if(!result.saved())result=session.cancel();});
    }
    static ContentEditResult openEditor(Window owner,QuestionContent content,RichContentEditorProfile profile,
            List<QBankResource> resources,QuestionResourceInput input) {
        if(profile!=RichContentEditorProfile.PROMPT)throw new IllegalArgumentException("Unsupported editor profile");
        return CanvasEditorWindow.openEditor(owner,"编辑题干",content,resources,input);
    }
    void show(){stage.show();}
    Stage stage(){return stage;}
    RichContentEditor editor(){return editor;}
    ContentEditResult result(){return result;}
    private void command(FlowPane toolbar,String text,String name) {
        button(toolbar,text,"command-"+name,()->attempt(()->editor.command(name,null)));
    }
    private void button(FlowPane toolbar,String text,String id,Runnable action) {
        var button=new Button(text);button.setId("rich-editor-"+id);button.setOnMousePressed(e->editor.captureSelection());button.setOnAction(e->action.run());toolbar.getChildren().add(button);
    }
    private void link() {
        var dialog=new TextInputDialog("https://");dialog.initOwner(stage);dialog.setTitle("插入链接");dialog.setHeaderText("输入链接地址");
        dialog.showAndWait().filter(s->!s.isBlank()).ifPresent(href->attempt(()->editor.command("link",href)));
    }
    private void chooseImage(boolean replace) {
        var chooser=new FileChooser();chooser.setTitle(replace?"替换图片":"插入图片");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG / JPEG 图片","*.png","*.jpg","*.jpeg","*.PNG","*.JPG","*.JPEG"));
        var file=chooser.showOpenDialog(stage);if(file!=null)attempt(()->image(file.toPath(),replace));
    }
    void image(Path path,boolean replace){if(replace)editor.replaceSelectedImage(path);else editor.insertImage(path);}
    private void save(){attempt(()->{result=session.save(editor.getContent());stage.close();});}
    private void cancel(){result=session.cancel();stage.close();}
    private void attempt(Runnable action){try{action.run();errors.setText("");}catch(RuntimeException failure){error(failure.getMessage());}}
    private void error(String message){errors.setText(message==null?"编辑失败":message);}
}
