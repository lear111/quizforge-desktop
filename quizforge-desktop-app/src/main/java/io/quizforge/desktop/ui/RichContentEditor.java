package io.quizforge.desktop.ui;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.*;
import javafx.scene.layout.VBox;

/** Reusable portable-content editor. Window controls and business transactions live elsewhere. */
final class RichContentEditor extends VBox {
    private final RichContentEditorEngine engine;
    private final Function<Path,QBankResource> importer;
    private final Consumer<QuestionContent> changed;
    RichContentEditor(ContentEditSession session,QuestionContent initial,Consumer<String> error) {
        this(initial,ignored->{},session::stage,session::resources,session::open,error);
    }
    RichContentEditor(QuestionContent content,Consumer<QuestionContent> changed,Function<Path,QBankResource> importer,
            Supplier<List<QBankResource>> resources,QuestionResourceInput input,Consumer<String> error) {
        this.importer=importer;this.changed=changed;setId("rich-content-editor");
        setMinWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        setPrefWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        setMaxWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        engine=new WebViewRichContentEngine(content,resources,input,error);getChildren().add(engine.view());
    }
    void setContent(QuestionContent content){engine.setContent(content);}
    QuestionContent getContent(){return engine.getContent();}
    boolean ready(){return engine.ready();}
    void captureSelection(){engine.captureSelection();}
    boolean imageSelected(){return engine.imageSelected();}
    void insertImage(Path path){engine.getContent();engine.insertImage(importer.apply(path));changed.accept(getContent());}
    void replaceSelectedImage(Path path){
        engine.getContent();if(!engine.imageSelected())throw new IllegalStateException("请先选择要替换的图片");
        engine.replaceSelectedImage(importer.apply(path));changed.accept(getContent());
    }
    void deleteSelectedImage(){engine.deleteSelectedImage();changed.accept(getContent());}
    void setImageWidth(int percent){engine.setImageWidth(percent);changed.accept(getContent());}
    void setImageAlignment(TextAlignment alignment){engine.setImageAlignment(alignment);changed.accept(getContent());}
    boolean command(String name,String argument){boolean result=engine.command(name,argument);changed.accept(getContent());return result;}
    RichContentEditorEngine engine(){return engine;}
}
