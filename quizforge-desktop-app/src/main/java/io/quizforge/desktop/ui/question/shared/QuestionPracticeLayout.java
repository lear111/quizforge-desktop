package io.quizforge.desktop.ui.question.shared;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;

/** Practice/editor column and its shared question outline. */
public final class QuestionPracticeLayout extends SplitPane {
    private final BorderPane readerColumn = new BorderPane();
    private final QuestionOutlineView outline;
    private boolean initialDividerSet;
    private io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost surface;
    public void setSurface(io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost surface,Node normal) {
        this.surface=surface;surface.setNormalContent(normal);readerColumn.setCenter(surface);
    }
    public io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost surface() { return surface; }

    public QuestionPracticeLayout(ScrollPane reader, QuestionOutlineView outline) {
        this.outline = outline;
        setId("practice-layout");
        getStyleClass().add("practice-browse-layout");
        setMinWidth(0);
        readerColumn.setMinWidth(320);
        readerColumn.setCenter(reader);
        getItems().addAll(readerColumn, outline);
        SplitPane.setResizableWithParent(outline, false);
        widthProperty().addListener((ignored, before, width) -> {
            if (initialDividerSet || width.doubleValue() <= 500) return;
            initialDividerSet = true;
            setDividerPositions((width.doubleValue() - 260) / width.doubleValue());
        });
    }

    public void setHeader(Node header) { readerColumn.setTop(header); }
    public void setContent(Node content) { readerColumn.setCenter(content); }
    public void keepDividerPosition(QuestionPracticeLayout previous) {
        initialDividerSet = true;
        setDividerPositions(previous.getDividerPositions());
    }
    public QuestionOutlineView outline() { return outline; }
}
