package io.quizforge.desktop.ui.markdown;

import io.quizforge.desktop.ui.shared.EditorUi;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.function.Consumer;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Edits the actual UTF-8 Markdown source without changing its syntax. */
public final class MarkdownSourceEditorView extends VBox {
    private final String original;
    private final TextArea source;
    private final Label error = UiTheme.label("", "editor-error");
    private final Runnable saveAction;

    public MarkdownSourceEditorView(String original, Consumer<String> save) {
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
        saveAction = () -> {
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

    public boolean dirty() { return !original.equals(source.getText()); }
    public void saveChanges() { saveAction.run(); }
}
