package io.quizforge.desktop.ui;

import io.quizforge.core.document.qdoc.QDocFileEditService;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionBankFileEditService;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceFileService;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import java.util.function.BiConsumer;
import javafx.scene.control.*;
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
            QDocFileEditService qdocEdits, QuestionBankFileEditService bankEdits) {
        this.workspaces = workspaces;
        this.files = files;
        this.history = history;
        this.stage = stage;
        setId("main-workspace");
        getStyleClass().add("workspace-shell");
        filePane = new FilePane(loader, references, ai, qdocEdits, bankEdits);
        sidebar = new WorkspaceSidebar(entry -> { if (current != null) filePane.open(current.id(), entry.relativePath()); }, settings);
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
                this::openWorkspace, this::newWorkspace, this::refresh);
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
