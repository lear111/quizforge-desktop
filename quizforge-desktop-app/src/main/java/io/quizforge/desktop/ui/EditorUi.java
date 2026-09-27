package io.quizforge.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

/** Quiet shared controls for the three document editors. */
final class EditorUi {
    private EditorUi() { }

    static HBox toolbar(String label, String id, Runnable save) {
        Button button = UiTheme.iconButton("save", "保存 · Ctrl+S", save);
        button.setId(id);
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox bar = new HBox(8, UiTheme.label(label, "editor-caption"), space, button);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("editor-toolbar");
        return bar;
    }

    static void saveShortcut(Parent editor, Runnable save) {
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
                save.run();
                event.consume();
            }
        });
    }

    static TextArea content(String value, String id, boolean code) {
        TextArea area = new TextArea(value);
        area.setId(id);
        area.setWrapText(true);
        area.getStyleClass().add(code ? "block-code-input" : "block-text-input");
        area.setMinHeight(Region.USE_PREF_SIZE);
        area.setMaxHeight(Region.USE_PREF_SIZE);
        Text measure = new Text();
        Runnable fit = () -> {
            measure.setFont(area.getFont());
            measure.setText(area.getText().isEmpty() ? " " : area.getText() + "\n");
            measure.setWrappingWidth(Math.max(80, area.getWidth() - 28));
            area.setPrefHeight(Math.max(46, Math.ceil(measure.getLayoutBounds().getHeight()) + 22));
        };
        area.textProperty().addListener((obs, before, after) -> fit.run());
        area.widthProperty().addListener((obs, before, after) -> fit.run());
        area.fontProperty().addListener((obs, before, after) -> fit.run());
        fit.run();
        return area;
    }

    static MenuButton menu(String icon, String description) {
        MenuButton button = new MenuButton("", UiTheme.icon(icon));
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        button.getStyleClass().add("editor-menu");
        return button;
    }
}
