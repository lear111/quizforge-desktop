package io.quizforge.desktop.ui;

import io.quizforge.core.workspace.Workspace;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

final class WorkspaceSwitcher extends MenuButton {
    WorkspaceSwitcher() {
        setId("workspace-switcher");
        setText("打开工作区");
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("workspace-switcher");
        setAccessibleText("切换工作区");
    }

    void update(Workspace current, List<Workspace> recent, Consumer<Workspace> select,
            Runnable open, Runnable create, Runnable refresh) {
        setText(current == null ? "打开工作区" : current.name());
        getItems().clear();
        for (Workspace workspace : recent.stream().limit(8).toList()) {
            MenuItem item = new MenuItem(workspace.name());
            item.setUserData(workspace.id());
            if (current != null && current.id().equals(workspace.id())) item.setGraphic(UiTheme.icon("check"));
            item.setOnAction(event -> select.accept(workspace));
            getItems().add(item);
        }
        if (!recent.isEmpty()) getItems().add(new SeparatorMenuItem());
        action("Open Workspace…", "open-workspace", open);
        action("New Workspace…", "new-workspace", create);
        MenuItem reload = action("Refresh", "refresh-workspace", refresh);
        reload.setDisable(current == null);
    }

    private MenuItem action(String title, String id, Runnable action) {
        MenuItem item = new MenuItem(title);
        item.setId(id);
        item.setOnAction(event -> action.run());
        getItems().add(item);
        return item;
    }
}
