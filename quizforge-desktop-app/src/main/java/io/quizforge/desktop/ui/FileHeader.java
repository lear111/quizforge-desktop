package io.quizforge.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

final class FileHeader extends HBox {
    private final Button mode;
    private Button historyButton;

    FileHeader(FilePresentation file, Runnable toggle, Runnable ai, Runnable history) {
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
        if (file.kind() == io.quizforge.core.workspace.WorkspaceFileKind.QUESTION_BANK) {
            historyButton = UiTheme.iconButton("clock", "历史记录", history);
            historyButton.setId("qbank-history-entry");
            getChildren().add(historyButton);
        }
        mode = UiTheme.iconButton("book", "切换到编辑模式", toggle);
        mode.setId("file-mode-toggle");
        mode.setDisable(file.file().questionBank() != null
                && !io.quizforge.core.question.QuestionText.supports(file.file().questionBank()));
        if (file.supportsMode()) getChildren().add(mode);
        if (file.offersAi()) {
            Button spark = UiTheme.iconButton("spark", "使用 AI 生成内容", ai);
            spark.setId("empty-asset-ai");
            getChildren().add(spark);
        }
        updateMode(FileMode.BROWSE);
    }

    void updateMode(FileMode current) {
        showHistory(historyButton != null || current == FileMode.BROWSE);
        mode.setGraphic(UiTheme.icon(current == FileMode.BROWSE ? "book" : "book-pen"));
        String action = current == FileMode.BROWSE ? "切换到编辑模式" : "切换到浏览模式";
        if (mode.isDisabled()) action = "当前编辑器暂不支持富内容和共享材料";
        mode.setTooltip(new Tooltip(action));
        mode.setAccessibleText(action);
    }

    void showHistory(boolean show) {
        if (historyButton == null) return;
        historyButton.setVisible(show);
        historyButton.setManaged(show);
    }

    void showMode(boolean show) {
        mode.setVisible(show);
        mode.setManaged(show);
    }
}
