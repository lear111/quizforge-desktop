package io.quizforge.desktop.ui.workspace;

import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import java.util.function.Consumer;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Workspace history scrolls independently of the fixed create/open actions. */
public final class WorkspaceSwitcher extends MenuButton {
    private static final int VISIBLE_HISTORY_ROWS = 7;
    private static final int HISTORY_ROW_HEIGHT = 32;
    private final VBox historyItems = new VBox();
    private final ScrollPane historyScroll = new ScrollPane(historyItems);
    private RotateTransition arrowTransition;

    public WorkspaceSwitcher() {
        setId("workspace-switcher");
        setText("打开工作区");
        setMaxWidth(Double.MAX_VALUE);
        setMinWidth(0);
        setWrapText(false);
        setTextOverrun(OverrunStyle.ELLIPSIS);
        getStyleClass().add("workspace-switcher");
        setAccessibleText("切换工作区");
        showingProperty().addListener((ignored, before, showing) -> rotateArrow(showing));
        historyItems.setId("workspace-history-list");
        historyItems.getStyleClass().add("workspace-history-list");
        historyScroll.setId("workspace-history-scroll");
        historyScroll.getStyleClass().add("workspace-history-scroll");
        historyScroll.setFitToWidth(true);
        historyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        historyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    }

    public void update(Workspace current, List<Workspace> recent, Consumer<Workspace> select,
            Runnable openFolder, Runnable create) {
        setText(current == null ? "打开工作区" : current.name());
        historyItems.getChildren().clear();
        if (recent.isEmpty()) {
            Label empty = new Label("尚无已打开的工作区");
            empty.getStyleClass().add("workspace-history-empty");
            historyItems.getChildren().add(empty);
        } else {
            for (Workspace workspace : recent) {
                boolean selected = current != null && current.id().equals(workspace.id());
                Button item = new Button(workspace.name(),
                        UiTheme.workspaceMenuIcon(selected ? "check" : "folder"));
                item.setId("workspace-recent-" + workspace.id());
                item.setUserData(workspace.id());
                item.setMinWidth(0);
                item.setMaxWidth(Double.MAX_VALUE);
                item.setTextOverrun(OverrunStyle.ELLIPSIS);
                item.getStyleClass().add("workspace-history-item");
                if (selected) item.getStyleClass().add("current");
                item.setOnAction(event -> {
                    hide();
                    select.accept(workspace);
                });
                historyItems.getChildren().add(item);
            }
        }
        int rows = Math.min(Math.max(recent.size(), 1), VISIBLE_HISTORY_ROWS);
        historyScroll.setPrefViewportHeight(rows * HISTORY_ROW_HEIGHT);
        historyScroll.setMaxHeight(VISIBLE_HISTORY_ROWS * HISTORY_ROW_HEIGHT);

        getItems().clear();
        CustomMenuItem history = new CustomMenuItem(historyScroll, false);
        history.setId("workspace-history");
        history.setText("");
        history.getStyleClass().add("workspace-history-menu-item");
        getItems().add(history);
        getItems().add(WorkspaceMenus.separator());
        getItems().add(WorkspaceMenus.action("新建工作区…", "new-workspace", "folder-plus", create));
        getItems().add(WorkspaceMenus.action("打开文件夹…", "open-workspace-folder", "folder-open", openFolder));
        getItems().forEach(WorkspaceMenus::headerItem);
    }

    public VBox historyItems() { return historyItems; }
    public ScrollPane historyScroll() { return historyScroll; }

    private void rotateArrow(boolean showing) {
        applyCss();
        Node arrow = lookup(".arrow-button .arrow");
        if (arrow == null) return;
        if (arrowTransition != null) arrowTransition.stop();
        arrowTransition = new RotateTransition(Duration.millis(180), arrow);
        arrowTransition.setFromAngle(arrow.getRotate());
        arrowTransition.setToAngle(showing ? 180 : 0);
        arrowTransition.setInterpolator(Interpolator.EASE_BOTH);
        arrowTransition.play();
    }
}
