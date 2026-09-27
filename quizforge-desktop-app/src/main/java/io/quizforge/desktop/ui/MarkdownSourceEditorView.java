package io.quizforge.desktop.ui;

import java.util.function.Consumer;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Edits the actual UTF-8 Markdown source without changing its syntax. */
final class MarkdownSourceEditorView extends VBox {
    private final String original;
    private final TextArea source;
    private final Label error = UiTheme.label("", "qdoc-error");

    MarkdownSourceEditorView(String original, Consumer<String> save) {
        this.original = original;
        setId("markdown-source-editor");
        getStyleClass().add("markdown-source-editor");
        setSpacing(10);
        setMaxWidth(820);
        source = new TextArea(original);
        source.setId("markdown-source-text");
        source.getStyleClass().add("document-editor");
        source.setWrapText(true);
        source.setPromptText("开始书写，支持 Markdown…");
        VBox.setVgrow(source, Priority.ALWAYS);
        error.setVisible(false);
        error.setManaged(false);
        Runnable saveAction = () -> {
            error.setVisible(false);
            error.setManaged(false);
            try { save.accept(source.getText()); }
            catch (RuntimeException failure) {
                error.setText("Could not save Markdown: " + failure.getMessage());
                error.setVisible(true);
                error.setManaged(true);
            }
        };
        getChildren().addAll(EditorUi.toolbar("Markdown", "markdown-save", saveAction), error, source);
        EditorUi.saveShortcut(this, saveAction);
    }

    boolean dirty() { return !original.equals(source.getText()); }
}
