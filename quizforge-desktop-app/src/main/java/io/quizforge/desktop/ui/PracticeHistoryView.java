package io.quizforge.desktop.ui;

import io.quizforge.core.practice.PracticeHistoryEntry;
import io.quizforge.core.practice.PracticeHistoryService;
import io.quizforge.core.practice.PracticeSummary;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.function.Consumer;
import javafx.geometry.Orientation;
import javafx.scene.input.MouseButton;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/** Archived rounds for the current QBank; card navigation and deletion stay separate. */
final class PracticeHistoryView extends VBox {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final PracticeHistoryService history;
    private final String bankAssetId;
    private final Consumer<String> openDetail;
    private final TilePane grid = new TilePane(Orientation.HORIZONTAL);
    private final Label error = UiTheme.label("", "warning");
    private Function<PracticeHistoryEntry, Boolean> deleteConfirmation = this::confirmDelete;

    PracticeHistoryView(PracticeHistoryService history, String bankAssetId, String bankName, Runnable back,
            Consumer<String> openDetail) {
        this.history = history;
        this.bankAssetId = bankAssetId;
        this.openDetail = openDetail;
        setId("practice-history");
        getStyleClass().add("history-view");
        Label title = UiTheme.label(bankName + " · 历史记录", "page-title");
        HBox.setHgrow(title, Priority.ALWAYS);
        Button returnButton = UiTheme.button("返回题库", "arrow-left", "", back);
        returnButton.setId("history-back");
        returnButton.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        title.setMinWidth(0);
        HBox heading = new HBox(12, returnButton, title);
        heading.getStyleClass().add("history-heading");
        grid.setId("history-grid");
        grid.getStyleClass().add("history-grid");
        grid.setPrefTileWidth(228);
        ScrollPane scroll = UiTheme.scroll(grid);
        scroll.setId("history-scroll");
        scroll.setFitToWidth(true);
        grid.prefWidthProperty().bind(scroll.viewportBoundsProperty().map(bounds -> Math.max(230, bounds.getWidth() - 48)));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        error.setId("history-error");
        error.setVisible(false);
        error.setManaged(false);
        getChildren().addAll(heading, error, scroll);
        refresh();
    }

    void setDeleteConfirmation(Function<PracticeHistoryEntry, Boolean> confirmation) {
        deleteConfirmation = confirmation;
    }

    void showLoadError(RuntimeException failure) {
        error.setText("无法读取练习详情：" + failure.getMessage());
        error.setVisible(true);
        error.setManaged(true);
    }

    private void refresh() {
        grid.getChildren().clear();
        var entries = history.listArchived(bankAssetId);
        if (entries.isEmpty()) {
            VBox empty = new VBox(8, UiTheme.label("暂无练习历史", "section-title"),
                    UiTheme.label("完成一次“重新练习”后，上一轮练习会保存在这里。", "muted"));
            empty.setId("history-empty");
            empty.getStyleClass().add("history-empty-state");
            grid.getChildren().add(empty);
        } else entries.forEach(entry -> grid.getChildren().add(card(entry)));
    }

    private VBox card(PracticeHistoryEntry entry) {
        PracticeSummary summary = entry.summary();
        VBox card = new VBox(12,
                UiTheme.label(date(entry.archivedAt()), "history-card-date"),
                UiTheme.label("正确率 " + accuracy(summary), "history-card-accuracy"),
                UiTheme.label(summary.totalCount() + " 道题", "history-card-meta"),
                UiTheme.label("正确 " + summary.correctCount() + " · 错误 " + summary.incorrectCount(), "history-card-meta"),
                UiTheme.label("未完成 " + summary.unfinishedCount(), "history-card-meta"));
        card.setId("history-card-" + entry.sessionId());
        card.getStyleClass().add("history-card");
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) openDetail.accept(entry.sessionId());
        });
        MenuItem delete = new MenuItem("删除历史记录");
        delete.setId("history-delete");
        delete.setOnAction(event -> delete(entry));
        ContextMenu menu = new ContextMenu(delete);
        card.getProperties().put("history.contextMenu", menu);
        card.setOnContextMenuRequested(event -> menu.show(card, event.getScreenX(), event.getScreenY()));
        return card;
    }

    private void delete(PracticeHistoryEntry entry) {
        if (!deleteConfirmation.apply(entry)) return;
        try {
            history.deleteArchivedSession(bankAssetId, entry.sessionId());
            error.setVisible(false);
            error.setManaged(false);
            refresh();
        } catch (RuntimeException failure) {
            error.setText("删除失败，历史记录已保留。" + failure.getMessage());
            error.setVisible(true);
            error.setManaged(true);
        }
    }

    private boolean confirmDelete(PracticeHistoryEntry entry) {
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType confirm = new ButtonType("删除", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "将永久删除本次练习及其所有作答记录。此操作无法撤销。\n"
                        + date(entry.archivedAt()) + " · " + entry.summary().totalCount() + " 道题 · 正确率 "
                        + accuracy(entry.summary()), cancel, confirm);
        alert.setHeaderText("删除历史记录？");
        if (getScene() != null) alert.initOwner(getScene().getWindow());
        UiTheme.apply(alert);
        alert.getDialogPane().lookupButton(confirm).getStyleClass().add("quiet-danger");
        return alert.showAndWait().orElse(cancel) == confirm;
    }

    private String accuracy(PracticeSummary summary) {
        return summary.accuracyPercent().isPresent() ? summary.accuracyPercent().getAsInt() + "%" : "—";
    }

    private String date(java.time.Instant instant) {
        return DATE.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }
}
