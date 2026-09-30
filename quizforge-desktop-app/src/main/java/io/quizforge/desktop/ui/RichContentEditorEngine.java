package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;
import javafx.scene.Node;

/** Engine boundary: portable content and resource identity, never HTML. */
interface RichContentEditorEngine {
    Node view();
    void setContent(QuestionContent content);
    QuestionContent getContent();
    boolean ready();
    void captureSelection();
    boolean imageSelected();
    void insertImage(QBankResource resource);
    void replaceSelectedImage(QBankResource resource);
    void deleteSelectedImage();
    void setImageWidth(int percent);
    void setImageAlignment(TextAlignment alignment);
    boolean command(String name,String argument);
}
