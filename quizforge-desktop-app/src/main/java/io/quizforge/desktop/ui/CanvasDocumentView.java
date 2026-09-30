package io.quizforge.desktop.ui;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.*;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Uses the same Canvas renderer as editing, without tools or a second layout conversion. */
final class CanvasDocumentView extends StackPane {
    private final CanvasEditorBridge bridge;
    CanvasDocumentView(DocumentContent content,List<QBankResource> resources,QuestionResourceInput input,String prefix) {
        setId(prefix+"native-document");setMinWidth(0);setMaxWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        setMinHeight(USE_PREF_SIZE);setMaxHeight(USE_PREF_SIZE);
        bridge=new CanvasEditorBridge(content,new ContentEditSession(content,resources,input),message->{
            var error=new Label("富文本文档无法显示："+message);error.setWrapText(true);getChildren().setAll(error);
        },()->{},true);
        bridge.view().setMinWidth(0);bridge.view().setPrefWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        bridge.view().setPrefHeight(80);getChildren().add(bridge.view());
        sceneProperty().addListener((o,old,current)->{
            if(old!=null && current==null)Platform.runLater(()->{if(getScene()==null)bridge.destroy();});
        });
    }
}
