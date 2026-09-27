package io.quizforge.desktop.ui;

import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceFileType;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
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
            Runnable open, Runnable create, Runnable refresh, Runnable createFolder,
            Consumer<WorkspaceFileType> createFile) {
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
        MenuItem reload = action("刷新", "refresh-workspace", refresh);
        reload.setDisable(current == null);
        if (current != null) {
            getItems().add(new SeparatorMenuItem());
            action("新建文件夹", "workspace-new-folder", createFolder);
            Menu files = new Menu("新建文件");
            files.setId("workspace-new-file");
            for (WorkspaceFileType type : WorkspaceFileType.values()) {
                MenuItem item = new MenuItem(type.extension());
                item.setId("workspace-new-" + type.name().toLowerCase(java.util.Locale.ROOT));
                item.setOnAction(event -> createFile.accept(type));
                files.getItems().add(item);
            }
            getItems().add(files);
        }
    }

    private MenuItem action(String title, String id, Runnable action) {
        MenuItem item = new MenuItem(title);
        item.setId(id);
        item.setOnAction(event -> action.run());
        getItems().add(item);
        return item;
    }
}
