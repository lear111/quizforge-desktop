package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.question.content.QuestionContent;
import java.io.IOException;
import java.io.InputStream;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/** Test-only access to the Canvas component's internal bridge. */
public final class CanvasEditorTestDriver {
    private final CanvasEditorWindow window;
    private CanvasEditorTestDriver(CanvasEditorWindow window){this.window=window;}
    public static CanvasEditorTestDriver from(Stage stage){
        return new CanvasEditorTestDriver((CanvasEditorWindow)stage.getProperties().get("quizforge.canvas.window"));
    }
    public CanvasEditorTestDriver bridge(){return this;}
    public ContentEditSession session(){return window.session();}
    public Stage stage(){return window.stage();}
    public boolean ready(){return window.bridge().ready();}
    public void loadContent(QuestionContent content){window.bridge().loadContent(content);}
    public QuestionContent getContent(){return window.bridge().getContent();}
    public WebView view(){return window.bridge().view();}
    public static String mediaType(){return CanvasNativeDocument.MEDIA_TYPE;}
    public static String readDocument(InputStream input)throws IOException{return CanvasNativeDocument.read(input);}
}
