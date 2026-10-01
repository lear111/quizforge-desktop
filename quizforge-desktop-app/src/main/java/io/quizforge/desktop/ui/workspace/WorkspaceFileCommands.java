package io.quizforge.desktop.ui.workspace;

import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.core.workspace.model.WorkspaceFileEntry;
import io.quizforge.core.workspace.model.WorkspaceFileKind;
import io.quizforge.core.workspace.model.WorkspaceFileType;
import io.quizforge.core.workspace.service.WorkspaceFileService;
import io.quizforge.desktop.ui.shared.TextClipboard;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.*;
import javafx.stage.Stage;

/** Coordinates file operations against the current workspace and open tabs. */
public final class WorkspaceFileCommands {
    private final WorkspaceFileService files;
    private final Supplier<Workspace> current;
    private final WorkspaceSidebar sidebar;
    private final WorkspaceTabManager tabs;
    private final Stage stage;
    private final TextClipboard clipboard;
    private final Runnable refreshTree;
    private final Consumer<String> selectPath;
    private final BooleanSupplier confirmDiscard;
    public WorkspaceFileCommands(WorkspaceFileService files,Supplier<Workspace> current,WorkspaceSidebar sidebar,
            WorkspaceTabManager tabs,Stage stage,TextClipboard clipboard,Runnable refreshTree,
            Consumer<String> selectPath,BooleanSupplier confirmDiscard) {
        this.files=files;this.current=current;this.sidebar=sidebar;this.tabs=tabs;this.stage=stage;
        this.clipboard=clipboard;this.refreshTree=refreshTree;this.selectPath=selectPath;this.confirmDiscard=confirmDiscard;
    }
    public void createFolder(String parent) {
        if (current.get() == null) return;
        sidebar.tree().beginCreateFolder(parent, name -> {
            String path = files.createFolder(current.get().id(), parent, name);
            refreshTree.run();
            selectPath.accept(path);
        });
    }

    public void createFile(String parent, WorkspaceFileType type) {
        if (current.get() == null) return;
        sidebar.tree().beginCreateFile(parent, type, name -> {
            String path = files.createFile(current.get().id(), parent, name, type);
            refreshTree.run();
            selectPath.accept(path);
        });
    }

    public void copyPath(String path, boolean absolute) {
        try {
            String value = absolute ? files.absolutePath(current.get().id(), path).toString() : path;
            copyText(value);
        } catch (RuntimeException error) { showFileError("无法复制文件路径", error); }
    }

    private void copyText(String value) {
        clipboard.write(value);
    }

    public void rename(WorkspaceFileEntry entry) {
        if (tabs.hasUnsavedChangesUnder(entry.relativePath()) && !confirmDiscard.getAsBoolean()) return;
        sidebar.tree().beginRename(entry, name -> {
            String active = activePath();
            boolean affectsActive = contains(entry.relativePath(), active);
            String renamed = files.rename(current.get().id(), entry.relativePath(), name);
            tabs.renameUnder(current.get().id(), entry.relativePath(), renamed);
            refreshTree.run();
            if (affectsActive) selectPath.accept(renamed + active.substring(entry.relativePath().length()));
            else selectPath.accept(renamed);
        });
    }

    public void delete(WorkspaceFileEntry entry) {
        boolean folder = entry.kind() == WorkspaceFileKind.DIRECTORY;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                folder ? "将永久删除此文件夹及其中所有文件，包括文件树中隐藏的文件。"
                        : "将永久删除此文件。",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setHeaderText("删除 “" + entry.name() + "”？");
        UiTheme.apply(confirm);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        boolean affectsActive = contains(entry.relativePath(), activePath());
        if (tabs.hasUnsavedChangesUnder(entry.relativePath()) && !confirmDiscard.getAsBoolean()) return;
        try {
            files.delete(current.get().id(), entry.relativePath());
            tabs.closeUnder(entry.relativePath());
            refreshTree.run();
        } catch (RuntimeException error) { showFileError("无法删除", error); }
    }

    private String activePath() {
        return tabs.active() == null ? null : tabs.active().path();
    }

    private boolean contains(String path, String active) {
        return active != null && (active.equals(path) || active.startsWith(path + "/"));
    }

    private void showFileError(String title, RuntimeException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        alert.initOwner(stage);
        UiTheme.apply(alert);
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
