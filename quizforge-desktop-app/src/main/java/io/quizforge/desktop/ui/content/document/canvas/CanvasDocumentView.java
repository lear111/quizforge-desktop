package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.desktop.ui.content.QuestionContentLayout;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Uses the same Canvas renderer as editing, without tools or a second layout conversion. */
public final class CanvasDocumentView extends StackPane {
    private CanvasEditorBridge bridge;
    private final DocumentContent content;
    private final List<QBankResource> resources;
    private final QuestionResourceInput input;
    private boolean released;
    public CanvasDocumentView(DocumentContent content,List<QBankResource> resources,QuestionResourceInput input,String prefix) {
        setId(prefix+"native-document");setMinWidth(0);setMaxWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        setMinHeight(USE_PREF_SIZE);setMaxHeight(USE_PREF_SIZE);
        this.content=content;this.resources=List.copyOf(resources);this.input=input;
        createBridge();
        sceneProperty().addListener((o,old,current)->{
            if(current!=null && released) createBridge();
            if(old!=null && current==null)Platform.runLater(()->{
                if(getScene()==null && !released){bridge.destroy();released=true;}
            });
        });
    }
    private void createBridge() {
        released=false;
        bridge=new CanvasEditorBridge(content,new ContentEditSession(content,resources,input),message->{
            var error=new Label("富文本文档无法显示："+message);error.setWrapText(true);getChildren().setAll(error);
        },()->{},true);
        bridge.view().setMinWidth(0);bridge.view().setPrefWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        bridge.view().setPrefHeight(80);getChildren().setAll(bridge.view());
    }
}
