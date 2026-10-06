package io.quizforge.desktop.ui.shell;

import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.document.navigation.QuizForgeNavigationLinkCodec;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.question.service.QuestionBankFileEditService;
import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import io.quizforge.core.question.source.QuestionSourceLinkService;
import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.core.workspace.model.WorkspaceFileEntry;
import io.quizforge.core.workspace.model.WorkspaceFileType;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.core.workspace.service.WorkspaceFileService;
import io.quizforge.core.workspace.service.WorkspaceService;
import io.quizforge.desktop.ui.file.FilePane;
import io.quizforge.desktop.ui.file.FilePresentationLoader;
import io.quizforge.desktop.ui.question.source.QuestionSourceNavigationAdapter;
import io.quizforge.desktop.ui.shared.TextClipboard;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.desktop.ui.workspace.WorkspaceFileCommands;
import io.quizforge.desktop.ui.workspace.WorkspaceFileTreeView;
import io.quizforge.desktop.ui.workspace.WorkspaceHistory;
import io.quizforge.desktop.ui.workspace.WorkspaceNavigationService;
import io.quizforge.desktop.ui.workspace.WorkspaceSidebar;
import io.quizforge.desktop.ui.workspace.WorkspaceTab;
import io.quizforge.desktop.ui.workspace.WorkspaceTabManager;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/** The desktop shell: one current workspace tree and a file tab strip. */
final class MainWorkspaceView extends WorkspaceLayout {
    private final WorkspaceService workspaces;
    private final WorkspaceFileService files;
    private final WorkspaceHistory history;
    private final Stage stage;
    private final TextClipboard clipboard;
    private final FilePane filePane;
    private final WorkspaceNavigationService navigation;
    private final QuizForgeNavigationLinkCodec navigationCodec = new QuizForgeNavigationLinkCodec();
    private Workspace current;
    private WorkspaceFileCommands fileCommands;

    MainWorkspaceView(WorkspaceService workspaces, WorkspaceFileService files, WorkspaceHistory history,
            FilePresentationLoader loader, QuestionBankReferenceResolver references, Stage stage,
            Runnable settings,
            QuestionBankFileEditService bankEdits,
            MarkdownFileEditService markdownEdits, MarkdownDocumentRegistration registration,
            TextClipboard clipboard, AssetIndexRepository index, QuestionSourceLinkService sourceLinks,
            io.quizforge.core.port.PracticeRuntimeProvider practice) {
        this.workspaces = workspaces;
        this.files = files;
        this.history = history;
        this.stage = stage;
        this.clipboard = clipboard;
        setId("main-workspace");
        getStyleClass().add("workspace-shell");
        var sourceNavigation = new QuestionSourceNavigationAdapter(references, sourceLinks,
                this::navigate, this::showNavigationStatus);
        filePane = new FilePane(loader, bankEdits,
                markdownEdits, registration, this::refreshTree, clipboard, sourceLinks, this::openNavigationUri,
                sourceNavigation, practice);
        tabs = new WorkspaceTabManager(() -> new FilePane(loader, bankEdits,
                markdownEdits, registration, this::refreshTree, clipboard, sourceLinks, this::openNavigationUri,
                sourceNavigation, practice),
                filePane, this::confirmDiscard);
        navigation = new WorkspaceNavigationService(index, () -> current == null ? null : current.id(), tabs);
        sidebar = new WorkspaceSidebar((entry, pinned) -> {
            if (current != null) {
                tabs.setBottom(null);
                if (pinned) tabs.openPinned(current.id(), entry.relativePath());
                else tabs.openPreview(current.id(), entry.relativePath());
            }
        }, new WorkspaceFileTreeView.FileActions() {

            @Override public void copyLink(WorkspaceFileEntry entry) {
                tabs.activePane().copyAssetLink(current.id(), entry);
            }
            @Override public void createFolder(String parent) { fileCommands.createFolder(parent); }
            @Override public void createFile(String parent, WorkspaceFileType type) { fileCommands.createFile(parent, type); }
            @Override public void copyPath(String path, boolean absolute) { fileCommands.copyPath(path, absolute); }
            @Override public void rename(WorkspaceFileEntry entry) { fileCommands.rename(entry); }
            @Override public void delete(WorkspaceFileEntry entry) { fileCommands.delete(entry); }
        }, settings, this::refresh, () -> fileCommands.createFolder(""), type -> fileCommands.createFile("", type));
        var extensions = UiTheme.iconButton("book", "题型扩展", () -> io.quizforge.desktop.extension.ExtensionManagementDialog.show(stage));
        extensions.setId("question-extensions-button");extensions.getStyleClass().add("sidebar-settings");
        var settingsArea = (javafx.scene.layout.HBox)sidebar.lookup("#workspace-settings-area");
        settingsArea.getChildren().add(extensions);
        fileCommands=new WorkspaceFileCommands(files,()->current,sidebar,tabs,stage,clipboard,
                this::refreshTree,this::selectPath,this::confirmDiscard);
        installLayout(stage);
        tabs.onActivate(path -> {
            trackActivePage();
            if (path != null) {
                var item = find(sidebar.tree().getRoot(), path);
                if (item != null) sidebar.tree().selectWithoutOpening(item);
            }
        });
        var available = history.order(workspaces.listWorkspaces());
        if (!available.isEmpty()) switchWorkspace(available.getFirst());
        else {
            updateSwitcher();
            filePane.setCenter(UiTheme.quietState("欢迎使用 QuizForge", "在左上角打开或新建一个工作区。"));
        }
    }

    void switchWorkspace(Workspace workspace) {
        if (tabs.hasUnsavedChanges() && !confirmDiscard()) return;
        finishWorkspaceSwitch(workspace);
    }

    private void finishWorkspaceSwitch(Workspace workspace) {
        if(!tabs.closeAll(()->finishWorkspaceSwitch(workspace)))return;
        sidebar.tree().setRoot(null);
        current = workspace;
        history.visit(workspace);
        updateSwitcher();
        refreshTree();
    }

    private void updateSwitcher() {
        sidebar.switcher().update(current, history.order(workspaces.listWorkspaces()), this::switchWorkspace,
                this::openWorkspaceFolder, this::newWorkspace);
        sidebar.setFileActionsEnabled(current != null);
    }

    boolean prepareExit() {
        if(tabs.tabs().stream().anyMatch(tab->!tab.pane().prepareClose()))return false;
        boolean confirmed=confirmExitChanges();if(!confirmed)tabs.tabs().forEach(tab->tab.pane().cancelClosePreparation());return confirmed;
    }
    java.util.concurrent.CompletionStage<Boolean> prepareExitAsync(){
        var result=new java.util.concurrent.CompletableFuture<Boolean>();
        tabs.prepareAllAsync().whenComplete((ok,failure)->{
            Runnable finish=()->{try{boolean confirmed=failure==null&&Boolean.TRUE.equals(ok)&&confirmExitChanges();if(!confirmed)tabs.tabs().forEach(tab->tab.pane().cancelClosePreparation());result.complete(confirmed);}catch(RuntimeException problem){tabs.tabs().forEach(tab->tab.pane().cancelClosePreparation());result.completeExceptionally(problem);}};
            if(javafx.application.Platform.isFxApplicationThread())finish.run();else javafx.application.Platform.runLater(finish);
        });return result.minimalCompletionStage();
    }
    private boolean confirmExitChanges(){
        if (!tabs.hasUnsavedChanges()) return true;
        ButtonType save = new ButtonType("保存并退出");
        ButtonType discard = new ButtonType("放弃修改");
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "有未保存的修改。", save, discard, ButtonType.CANCEL);
        confirm.initOwner(stage);
        confirm.setHeaderText("关闭 QuizForge");
        UiTheme.apply(confirm);
        ButtonType choice = confirm.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == discard) return true;
        if (choice != save) return false;
        for (WorkspaceTab tab : tabs.tabs()) {
            if (!tab.pane().saveUnsavedChanges()) return false;
        }
        return !tabs.hasUnsavedChanges();
    }

    private boolean confirmDiscard() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "当前文件有未保存的修改，继续操作会丢弃这些修改。", ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setHeaderText("继续操作？");
        UiTheme.apply(confirm);
        return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void refreshTree() {
        if (current == null) return;
        try {
            var snapshot = files.refresh(current.id());
            sidebar.tree().replace(snapshot);
            sidebar.switcher().setTooltip(snapshot.registryWarning() == null ? null
                    : new Tooltip("文件树已更新；资产索引需要刷新。"));
        } catch (RuntimeException error) {
            tabs.activePane().setCenter(UiTheme.quietState("无法读取工作区", "请检查文件夹是否可访问，然后使用工作区菜单刷新。"));
        }
    }

    void refresh() {
        if (tabs.hasUnsavedChanges() && !confirmDiscard()) return;
        if (current == null) return;
        String path = tabs.active() == null ? null : tabs.active().path();
        refreshTree();
        tabs.reloadAll(current.id());
        if (path != null) selectPath(path);
    }

    void refreshAndOpen(WorkspaceId workspace, String path) {
        if (current == null || !current.id().equals(workspace)) return;
        if (tabs.hasUnsavedChangesUnder(path) && !confirmDiscard()) return;
        tabs.closeUnder(path);
        refreshTree();
        tabs.openPinned(workspace, path);
        selectPath(path);
    }

    void selectPath(String path) {
        var item = find(sidebar.tree().getRoot(), path);
        if (item != null) sidebar.tree().getSelectionModel().select(item);
    }

    private TreeItem<io.quizforge.core.workspace.model.WorkspaceFileEntry> find(
            TreeItem<io.quizforge.core.workspace.model.WorkspaceFileEntry> item, String path) {
        if (item == null) return null;
        if (item.getValue() != null && item.getValue().relativePath().equals(path)) return item;
        for (var child : item.getChildren()) {
            var result = find(child, path);
            if (result != null) return result;
        }
        return null;
    }

    private void openWorkspaceFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择已有的 QuizForge 工作区文件夹");
        chooser.setInitialDirectory(workspaces.defaultWorkspaceParent().toFile());
        java.io.File folder = chooser.showDialog(stage);
        if (folder == null) return;
        try {
            switchWorkspace(workspaces.registerExistingWorkspace(folder.toPath()));
        } catch (RuntimeException error) {
            showWorkspaceError("无法打开工作区文件夹", error);
        }
    }

    private void newWorkspace() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择新建工作区的位置");
        chooser.setInitialDirectory(workspaces.defaultWorkspaceParent().toFile());
        java.io.File parent = chooser.showDialog(stage);
        if (parent == null) return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(stage);
        UiTheme.apply(dialog);
        dialog.setTitle("New Workspace");
        dialog.setHeaderText("新建工作区 · " + parent.getAbsolutePath());
        dialog.setContentText("名称");
        dialog.showAndWait().ifPresent(name -> {
            try { switchWorkspace(workspaces.createWorkspace(name, parent.toPath())); }
            catch (RuntimeException error) {
                Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
                alert.initOwner(stage);
                UiTheme.apply(alert);
                alert.setHeaderText("无法创建工作区");
                alert.showAndWait();
            }
        });
    }

    private void showWorkspaceError(String title, RuntimeException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        alert.initOwner(stage);
        alert.setHeaderText(title);
        UiTheme.apply(alert);
        alert.showAndWait();
    }

    Workspace currentWorkspace() { return current; }
    WorkspaceSidebar sidebar() { return sidebar; }
    FilePane filePane() { return tabs.activePane(); }
    WorkspaceTabManager tabs() { return tabs; }
    WorkspaceNavigationService.Result navigate(QuizForgeNavigationLink link) {
        WorkspaceNavigationService.Result result = navigation.navigate(link);
        if (result != WorkspaceNavigationService.Result.OPENED) {
            String message = switch (result) {
                case MISSING_ASSET -> "当前工作区找不到这个文档资产。";
                case MISSING_TARGET -> "文档中找不到指定位置。";
                case EDIT_MODE -> "目标文件正在编辑，请先切换到浏览模式。";
                case UNAVAILABLE_FILE -> "目标文件无法打开。";
                default -> "";
            };
            showNavigationStatus(message);
        } else tabs.setBottom(null);
        return result;
    }

    private void openNavigationUri(String uri) {
        try {
            navigate(navigationCodec.decode(uri));
        } catch (IllegalArgumentException error) {
            showNavigationStatus("链接格式无效。");
        }
    }

    private void showNavigationStatus(String message) {
        Label status = new Label(message);
        status.getStyleClass().add("workspace-navigation-status");
        tabs.setBottom(status);
    }
}
