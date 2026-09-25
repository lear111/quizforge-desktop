package io.quizforge.desktop.ui;

import io.quizforge.core.workspace.WorkspaceFileEntry;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class WorkspaceSidebar extends VBox {
    private final WorkspaceSwitcher switcher = new WorkspaceSwitcher();
    private final WorkspaceFileTree tree;

    WorkspaceSidebar(Consumer<WorkspaceFileEntry> open, Runnable settings) {
        setId("workspace-sidebar");
        getStyleClass().add("workspace-sidebar");
        setMinWidth(190);
        setPrefWidth(250);
        setMaxWidth(600);
        tree = new WorkspaceFileTree(open);
        tree.setMinHeight(0);
        VBox.setVgrow(tree, Priority.ALWAYS);
        Button settingsButton = UiTheme.iconButton("settings", "设置", settings);
        settingsButton.getStyleClass().add("sidebar-settings");
        settingsButton.setId("settings-button");
        HBox footer = new HBox(settingsButton);
        footer.setId("workspace-settings-area");
        footer.getStyleClass().add("workspace-settings-area");
        getChildren().addAll(switcher, tree, footer);
    }

    WorkspaceSwitcher switcher() { return switcher; }
    WorkspaceFileTree tree() { return tree; }
}
