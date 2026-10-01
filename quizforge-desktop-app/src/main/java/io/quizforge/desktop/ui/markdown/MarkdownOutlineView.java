package io.quizforge.desktop.ui.markdown;

import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** A document-scoped outline; a new preview creates a new selection and navigator. */
final class MarkdownOutlineView extends VBox {
    private Button selected;
    private final ScrollPane list;

    MarkdownOutlineView(List<MarkdownOutline.Entry> entries, MarkdownDocumentNavigator navigator,
            Consumer<MarkdownOutline.Entry> copyLink) {
        setId("markdown-outline");
        getStyleClass().add("markdown-outline");
        Label title = UiTheme.label("大纲", "markdown-outline-title");
        VBox rows = new VBox();
        rows.getStyleClass().add("markdown-outline-rows");
        for (MarkdownOutline.Entry entry : entries)
            rows.getChildren().add(row(entry, navigator, copyLink));
        list = UiTheme.scroll(rows);
        list.getStyleClass().add("markdown-outline-scroll");
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(title, list);
    }

    ScrollPane scroll() { return list; }

    private Button row(MarkdownOutline.Entry entry, MarkdownDocumentNavigator navigator,
            Consumer<MarkdownOutline.Entry> copyLink) {
        Region indent = new Region();
        indent.setMinWidth(entry.depth() * 14);
        indent.setPrefWidth(entry.depth() * 14);
        Label label = UiTheme.label(entry.label(), "markdown-outline-label");
        label.setWrapText(false);
        HBox content = new HBox(indent);
        if (entry.kind() == MarkdownOutline.Kind.ANCHOR) {
            content.getChildren().add(UiTheme.label("↗", "markdown-outline-anchor-icon"));
        }
        content.getChildren().add(label);
        Button button = new Button("", content);
        button.setId(entry.runtimeId());
        button.getStyleClass().addAll("markdown-outline-entry",
                entry.kind() == MarkdownOutline.Kind.ANCHOR ? "markdown-outline-anchor" : "markdown-outline-heading");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAccessibleText(entry.label());
        button.getProperties().put("quizforge.navigationTarget", entry);
        if (entry.orphan()) {
            button.getStyleClass().add("markdown-outline-orphan");
            button.setDisable(true);
            button.setTooltip(new Tooltip("锚点没有可定位的内容"));
        } else {
            button.setOnAction(event -> {
                if (!navigator.jumpTo(entry)) return;
                if (selected != null) selected.getStyleClass().remove("markdown-outline-selected");
                selected = button;
                button.getStyleClass().add("markdown-outline-selected");
            });
            if (copyLink != null) {
                MenuItem copy = new MenuItem("Copy Link");
                copy.setId("outline-copy-link");
                copy.setOnAction(event -> copyLink.accept(entry));
                button.setContextMenu(new ContextMenu(copy));
            }
        }
        return button;
    }
}
