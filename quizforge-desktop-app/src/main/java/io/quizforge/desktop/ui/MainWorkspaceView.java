package io.quizforge.desktop.ui;

import io.quizforge.core.document.qdoc.QDocFileEditService;
import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionBankFileEditService;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceFileService;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceFileType;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import java.util.function.BiConsumer;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/** The entire desktop shell: one current workspace tree and one current file. */
final class MainWorkspaceView extends BorderPane {
    private final WorkspaceService workspaces;
    private final WorkspaceFileService files;
    private final WorkspaceHistory history;
    private final Stage stage;
    private final WorkspaceSidebar sidebar;
    private final FilePane filePane;
    private Workspace current;

    MainWorkspaceView(WorkspaceService workspaces, WorkspaceFileService files, WorkspaceHistory history,
            FilePresentationLoader loader, QuestionBankReferenceResolver references, Stage stage,
            Runnable settings, BiConsumer<WorkspaceId, FilePresentation> ai,
            QDocFileEditService qdocEdits, QuestionBankFileEditService bankEdits,
            MarkdownFileEditService markdownEdits) {
        this.workspaces = workspaces;
        this.files = files;
        this.history = history;
        this.stage = stage;
        setId("main-workspace");
        getStyleClass().add("workspace-shell");
        filePane = new FilePane(loader, references, ai, qdocEdits, bankEdits,
                markdownEdits, this::refreshTree);
        sidebar = new WorkspaceSidebar(entry -> {
            if (current != null) filePane.open(current.id(), entry.relativePath());
        }, new WorkspaceFileTree.FileActions() {
            @Override public void createFolder(String parent) { MainWorkspaceView.this.createFolder(parent); }
            @Override public void createFile(String parent, WorkspaceFileType type) {
                MainWorkspaceView.this.createFile(parent, type);
            }
            @Override public void copyPath(String path, boolean absolute) {
                MainWorkspaceView.this.copyPath(path, absolute);
            }
            @Override public void rename(WorkspaceFileEntry entry) { MainWorkspaceView.this.rename(entry); }
            @Override public void delete(WorkspaceFileEntry entry) { MainWorkspaceView.this.delete(entry); }
        }, settings);
        filePane.setMinWidth(320);
        SplitPane split = new SplitPane(sidebar, filePane);
        split.setId("workspace-split");
        split.getStyleClass().add("workspace-split");
        SplitPane.setResizableWithParent(sidebar, false);
        split.widthProperty().addListener((obs, before, width) -> {
            if (before.doubleValue() == 0 && width.doubleValue() > 0) {
                split.setDividerPositions(250 / width.doubleValue());
            }
        });
        setCenter(split);
        var available = history.order(workspaces.listWorkspaces());
        if (!available.isEmpty()) switchWorkspace(available.getFirst());
        else {
            updateSwitcher();
            filePane.setCenter(UiTheme.quietState("欢迎使用 QuizForge", "在左上角打开或新建一个工作区。"));
        }
    }

    void switchWorkspace(Workspace workspace) {
        filePane.clear();
        sidebar.tree().setRoot(null);
        current = workspace;
        history.visit(workspace);
        updateSwitcher();
        refreshTree();
    }

    private void updateSwitcher() {
        sidebar.switcher().update(current, history.order(workspaces.listWorkspaces()), this::switchWorkspace,
                this::openWorkspace, this::newWorkspace, this::refresh,
                () -> createFolder(""), type -> createFile("", type));
    }

    private void createFolder(String parent) {
        askName("新建文件夹", "文件夹名称", "").ifPresent(name -> {
            try {
                String path = files.createFolder(current.id(), parent, name);
                refreshTree();
                selectPath(path);
            } catch (RuntimeException error) { showFileError("无法新建文件夹", error); }
        });
    }

    private void createFile(String parent, WorkspaceFileType type) {
        askName("新建 " + type.extension() + " 文件", "文件名称", "").ifPresent(name -> {
            try {
                String path = files.createFile(current.id(), parent, name, type);
                refreshTree();
                selectPath(path);
            } catch (RuntimeException error) { showFileError("无法新建文件", error); }
        });
    }

    private void copyPath(String path, boolean absolute) {
        try {
            String value = absolute ? files.absolutePath(current.id(), path).toString() : path;
            ClipboardContent content = new ClipboardContent();
            content.putString(value);
            Clipboard.getSystemClipboard().setContent(content);
        } catch (RuntimeException error) { showFileError("无法复制文件路径", error); }
    }

    private void rename(WorkspaceFileEntry entry) {
        askName("重命名", "新名称", entry.name()).ifPresent(name -> {
            String active = activePath();
            boolean affectsActive = contains(entry.relativePath(), active);
            if (affectsActive && filePane.hasUnsavedChanges() && !confirmDiscard()) return;
            try {
                String renamed = files.rename(current.id(), entry.relativePath(), name);
                if (affectsActive) filePane.clear();
                refreshTree();
                if (affectsActive) selectPath(renamed + active.substring(entry.relativePath().length()));
                else selectPath(renamed);
            } catch (RuntimeException error) { showFileError("无法重命名", error); }
        });
    }

    private void delete(WorkspaceFileEntry entry) {
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
        if (affectsActive && filePane.hasUnsavedChanges() && !confirmDiscard()) return;
        try {
            files.delete(current.id(), entry.relativePath());
            if (affectsActive) filePane.clear();
            refreshTree();
        } catch (RuntimeException error) { showFileError("无法删除", error); }
    }

    private boolean confirmDiscard() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "当前文件有未保存的修改，继续操作会丢弃这些修改。", ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setHeaderText("继续操作？");
        UiTheme.apply(confirm);
        return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private String activePath() {
        return filePane.currentFile() == null ? null : filePane.currentFile().file().entry().relativePath();
    }

    private boolean contains(String path, String active) {
        return active != null && (active.equals(path) || active.startsWith(path + "/"));
    }

    private java.util.Optional<String> askName(String title, String label, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.initOwner(stage);
        UiTheme.apply(dialog);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(label);
        return dialog.showAndWait();
    }

    private void showFileError(String title, RuntimeException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        alert.initOwner(stage);
        UiTheme.apply(alert);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private void refreshTree() {
        if (current == null) return;
        try {
            var snapshot = files.refresh(current.id());
            sidebar.tree().replace(snapshot);
            sidebar.switcher().setTooltip(snapshot.registryWarning() == null ? null
                    : new Tooltip("文件树已更新；资产索引需要刷新。"));
        } catch (RuntimeException error) {
            filePane.setCenter(UiTheme.quietState("无法读取工作区", "请检查文件夹是否可访问，然后使用工作区菜单刷新。"));
        }
    }

    void refresh() {
        if (filePane.hasUnsavedChanges() && !confirmDiscard()) return;
        String path = filePane.currentFile() == null ? null : filePane.currentFile().file().entry().relativePath();
        filePane.clear();
        refreshTree();
        if (path != null) selectPath(path);
    }

    void refreshAndOpen(WorkspaceId workspace, String path) {
        if (current == null || !current.id().equals(workspace)) return;
        filePane.clear();
        refreshTree();
        selectPath(path);
    }

    void selectPath(String path) {
        var item = find(sidebar.tree().getRoot(), path);
        if (item != null) sidebar.tree().getSelectionModel().select(item);
    }

    private TreeItem<io.quizforge.core.workspace.WorkspaceFileEntry> find(
            TreeItem<io.quizforge.core.workspace.WorkspaceFileEntry> item, String path) {
        if (item == null) return null;
        if (item.getValue() != null && item.getValue().relativePath().equals(path)) return item;
        for (var child : item.getChildren()) {
            var result = find(child, path);
            if (result != null) return result;
        }
        return null;
    }

    private void openWorkspace() {
        var available = workspaces.listWorkspaces();
        if (available.isEmpty()) { newWorkspace(); return; }
        Dialog<Workspace> dialog = new Dialog<>();
        dialog.initOwner(stage);
        UiTheme.apply(dialog);
        dialog.setTitle("Open Workspace");
        dialog.setHeaderText("选择已登记的工作区");
        ListView<Workspace> list = new ListView<>();
        list.getItems().setAll(history.order(available));
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Workspace workspace, boolean empty) {
                super.updateItem(workspace, empty);
                setText(empty || workspace == null ? null : workspace.name());
            }
        });
        list.getSelectionModel().selectFirst();
        list.setPrefSize(340, 260);
        dialog.getDialogPane().setContent(list);
        ButtonType open = new ButtonType("打开", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, open);
        dialog.setResultConverter(button -> button == open ? list.getSelectionModel().getSelectedItem() : null);
        dialog.showAndWait().ifPresent(this::switchWorkspace);
    }

    private void newWorkspace() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(stage);
        UiTheme.apply(dialog);
        dialog.setTitle("New Workspace");
        dialog.setHeaderText("新建工作区");
        dialog.setContentText("名称");
        dialog.showAndWait().ifPresent(name -> {
            try { switchWorkspace(workspaces.createWorkspace(name)); }
            catch (RuntimeException error) {
                Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
                alert.initOwner(stage);
                UiTheme.apply(alert);
                alert.setHeaderText("无法创建工作区");
                alert.showAndWait();
            }
        });
    }

    Workspace currentWorkspace() { return current; }
    WorkspaceSidebar sidebar() { return sidebar; }
    FilePane filePane() { return filePane; }
}
