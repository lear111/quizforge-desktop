package io.quizforge.desktop.ui;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shared reading geometry and navigation visuals; every caller keeps its own actions. */
final class QuestionCardLayout {
    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");
    private QuestionCardLayout() { }

    static void configure(VBox reader) {
        reader.getStyleClass().add("question-reader");
        reader.setMinWidth(0);
        reader.setMaxHeight(Region.USE_PREF_SIZE);
        reader.widthProperty().addListener((ignored, before, width) ->
                reader.pseudoClassStateChanged(COMPACT, width.doubleValue() < 520));
    }

    static HBox row(Node previous, VBox card, Node next) {
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox row = new HBox(14, previous, card, next);
        row.getStyleClass().add("question-navigation");
        row.setMinWidth(0);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    static Button navigation(String icon, String description, Runnable action) {
        Button button = UiTheme.iconButton(icon, description, action);
        button.getStyleClass().add("question-navigation-button");
        return button;
    }

    static ScrollPane scroll(VBox content) {
        StackPane aligned = new StackPane(content);
        aligned.setMinWidth(0);
        aligned.setAlignment(Pos.CENTER);
        ScrollPane scroll = UiTheme.scroll(aligned);
        scroll.setMinWidth(0);
        aligned.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty()));
        return scroll;
    }
}
