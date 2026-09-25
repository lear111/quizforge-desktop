package io.quizforge.desktop.ui;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/** Shared visual primitives for the desktop workspace and its dialogs. */
final class UiTheme {
    private UiTheme() { }

    static String stylesheet() {
        return Objects.requireNonNull(UiTheme.class.getResource("workspace.css")).toExternalForm();
    }

    static void apply(Scene scene) {
        scene.getStylesheets().add(stylesheet());
    }

    static void apply(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(stylesheet());
        dialog.getDialogPane().getStyleClass().add("workspace-dialog");
    }

    static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }

    static Button button(String text, String icon, String style, Runnable action) {
        Button button = new Button(text, icon(icon));
        button.getStyleClass().add(style);
        button.setOnAction(event -> action.run());
        return button;
    }

    static Button iconButton(String icon, String description, Runnable action) {
        Button button = button("", icon, "icon-button", action);
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    static Node icon(String name) {
        SVGPath path = new SVGPath();
        path.setContent(switch (name) {
            case "folder" -> "M2 5 L8 5 L10 7 L18 7 L18 17 L2 17 Z";
            case "file" -> "M5 2 L12 2 L17 7 L17 18 L5 18 Z M12 2 L12 7 L17 7 M8 11 L14 11 M8 14 L14 14";
            case "book" -> "M10 5 Q6 2 1 4 L1 17 Q6 15 10 18 Q14 15 19 17 L19 4 Q14 2 10 5 Z M10 5 L10 18";
            case "book-pen" -> "M9 5 Q5 2 1 4 L1 17 Q5 15 9 18 L9 5 Q13 2 18 4 L18 9 M11 17 L12 13 L17 8 L20 11 L15 16 Z M16 9 L19 12";
            case "markdown" -> "M3 2 L13 2 L17 6 L17 18 L3 18 Z M6 14 L6 8 L9 11 L12 8 L12 14";
            case "qbank" -> "M3 2 L17 2 L17 18 L3 18 Z M7 7 Q7 4 10 4 Q14 4 13 7 L10 10 L10 12 M10 15 L10 15.2";
            case "question" -> "M19 10 A9 9 0 1 1 1 10 A9 9 0 1 1 19 10 M7 7 Q7 4 10 4 Q14 4 13 7 L10 10 L10 12 M10 15 L10 15.2";
            case "chevron" -> "M5 8 L10 13 L15 8";
            case "check" -> "M3 10 L8 15 L17 5";
            case "refresh" -> "M17 7 A7 7 0 1 0 17 13 M17 2 L17 7 L12 7";
            case "grid" -> "M2 2 L8 2 L8 8 L2 8 Z M12 2 L18 2 L18 8 L12 8 Z M2 12 L8 12 L8 18 L2 18 Z M12 12 L18 12 L18 18 L12 18 Z";
            case "plus" -> "M10 3 L10 17 M3 10 L17 10";
            case "settings" -> "M8 1 L12 1 L12.5 3.3 L14.2 4.3 L16.5 3.6 L18.5 7 L16.7 8.6 L16.7 11.4 L18.5 13 L16.5 16.4 L14.2 15.7 L12.5 16.7 L12 19 L8 19 L7.5 16.7 L5.8 15.7 L3.5 16.4 L1.5 13 L3.3 11.4 L3.3 8.6 L1.5 7 L3.5 3.6 L5.8 4.3 L7.5 3.3 Z M13 10 A3 3 0 1 1 7 10 A3 3 0 1 1 13 10";
            case "panel" -> "M2 3 L18 3 L18 17 L2 17 Z M7 3 L7 17";
            case "upload" -> "M10 13 L10 2 M6 6 L10 2 L14 6 M3 12 L3 18 L17 18 L17 12";
            case "arrow" -> "M3 10 L17 10 M12 5 L17 10 L12 15";
            case "spark" -> "M10 2 L12 8 L18 10 L12 12 L10 18 L8 12 L2 10 L8 8 Z";
            default -> throw new IllegalArgumentException("Unknown icon: " + name);
        });
        path.getStyleClass().add("line-icon");
        StackPane box = new StackPane(path);
        box.setMinSize(20, 20);
        box.setPrefSize(20, 20);
        box.setMaxSize(20, 20);
        box.setAccessibleText(name);
        box.getStyleClass().add("icon-" + name);
        return box;
    }

    static ScrollPane scroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    static VBox emptyState(String icon, String title, String description) {
        StackPane mark = new StackPane(icon(icon));
        mark.getStyleClass().add("empty-mark");
        mark.setMaxSize(48, 48);
        VBox box = new VBox(14, mark, label(title, "section-title"), label(description, "muted"));
        box.getStyleClass().add("empty-state");
        return box;
    }

    static VBox quietState(String title, String description) {
        VBox box = new VBox(10, label(title, "quiet-state-title"), label(description, "muted"));
        box.getStyleClass().add("quiet-state");
        return box;
    }
}
