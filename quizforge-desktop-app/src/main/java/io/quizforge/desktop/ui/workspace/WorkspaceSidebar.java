package io.quizforge.desktop.ui.workspace;

import io.quizforge.core.workspace.model.WorkspaceFileEntry;
import io.quizforge.core.workspace.model.WorkspaceFileType;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class WorkspaceSidebar extends VBox {
    private final WorkspaceSwitcher switcher = new WorkspaceSwitcher();
    private final WorkspaceFileTreeView tree;
    private final HBox fileActions;

    public WorkspaceSidebar(BiConsumer<WorkspaceFileEntry, Boolean> open, WorkspaceFileTreeView.FileActions actions,
            Runnable settings, Runnable refresh, Runnable createFolder,
            Consumer<WorkspaceFileType> createFile) {
        setId("workspace-sidebar");
        getStyleClass().add("workspace-sidebar");
        setMinWidth(190);
        setPrefWidth(250);
        setMaxWidth(600);
        tree = new WorkspaceFileTreeView(open, actions);
        tree.setMinHeight(0);
        VBox.setVgrow(tree, Priority.ALWAYS);
        Button refreshButton = fileAction("refresh-workspace", "refresh", "刷新文件列表", refresh);
        Button folderButton = fileAction("workspace-new-folder", "folder-plus", "新建文件夹", createFolder);
        Button fileButton = fileAction("workspace-new-file", "file-plus", "新建文件", () -> { });
        ContextMenu fileTypes = new ContextMenu();
        for (WorkspaceFileType type : WorkspaceFileType.values()) {
            MenuItem item = WorkspaceMenus.textAction(
                    type == WorkspaceFileType.MARKDOWN ? "Markdown 文件 (.md)" : "题库文件 (.qbank)",
                    "workspace-new-" + type.name().toLowerCase(java.util.Locale.ROOT),
                    () -> createFile.accept(type));
            fileTypes.getItems().add(item);
        }
        fileButton.setContextMenu(fileTypes);
        fileButton.setOnAction(event -> fileTypes.show(fileButton, Side.BOTTOM, 0, 0));
        fileActions = new HBox(2, refreshButton, folderButton, fileButton);
        fileActions.setId("workspace-file-actions");
        fileActions.getStyleClass().add("workspace-file-actions");
        setFileActionsEnabled(false);
        Button settingsButton = UiTheme.iconButton("settings", "设置", settings);
        settingsButton.getStyleClass().add("sidebar-settings");
        settingsButton.setId("settings-button");
        HBox footer = new HBox(settingsButton);
        footer.setId("workspace-settings-area");
        footer.getStyleClass().add("workspace-settings-area");
        getChildren().addAll(switcher, fileActions, tree, footer);
    }

    private Button fileAction(String id, String icon, String tooltip, Runnable action) {
        Button button = new Button("", UiTheme.workspaceMenuIcon(icon));
        button.setId(id);
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        button.getStyleClass().add("workspace-file-action");
        button.setOnAction(event -> action.run());
        return button;
    }

    public void setFileActionsEnabled(boolean enabled) {
        fileActions.getChildren().forEach(action -> action.setDisable(!enabled));
    }

    public WorkspaceSwitcher switcher() { return switcher; }
    public WorkspaceFileTreeView tree() { return tree; }
    public HBox fileActions() { return fileActions; }
}
