package io.quizforge.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

final class FileHeader extends HBox {
    private final Button mode;

    FileHeader(FilePresentation file, Runnable toggle, java.util.function.Consumer<Button> details, Runnable ai) {
        setId("file-header");
        getStyleClass().add("file-header");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(4);
        Label name = UiTheme.label(file.file().entry().name(), "file-title");
        name.setId("file-title");
        name.setWrapText(false);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        getChildren().add(name);
        mode = UiTheme.iconButton("book", "切换到编辑模式", toggle);
        mode.setId("file-mode-toggle");
        if (file.supportsMode()) getChildren().add(mode);
        if (file.asset()) {
            Button info = UiTheme.iconButton("question", "文件详细信息", () -> { });
            info.setId("asset-info-button");
            info.setOnAction(event -> details.accept(info));
            getChildren().add(info);
        }
        if (file.offersAi()) {
            Button spark = UiTheme.iconButton("spark", "使用 AI 生成内容", ai);
            spark.setId("empty-asset-ai");
            getChildren().add(spark);
        }
        updateMode(FileMode.BROWSE);
    }

    void updateMode(FileMode current) {
        mode.setGraphic(UiTheme.icon(current == FileMode.BROWSE ? "book" : "book-pen"));
        String action = current == FileMode.BROWSE ? "切换到编辑模式" : "切换到浏览模式";
        mode.setTooltip(new Tooltip(action));
        mode.setAccessibleText(action);
    }
}
