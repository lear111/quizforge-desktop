package io.quizforge.desktop.ui;

import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.document.navigation.QuizForgeNavigationLinkCodec;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionBankFileEditService;
import io.quizforge.core.question.QuestionSourceLinkService;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceFileService;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceFileType;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;

/** The desktop shell: one current workspace tree and a file tab strip. */
final class MainWorkspaceView extends BorderPane {
    private static final double DIVIDER_HANDLE_WIDTH = 6;
    private static final double DIVIDER_LINE_WIDTH = 0.75;
    private static final PseudoClass DIVIDER_ACTIVE = PseudoClass.getPseudoClass("divider-active");
    private final WorkspaceService workspaces;
    private final WorkspaceFileService files;
    private final WorkspaceHistory history;
    private final Stage stage;
    private final TextClipboard clipboard;
    private final WorkspaceSidebar sidebar;
    private final FilePane filePane;
    private final WorkspaceTabManager tabs;
    private final SplitPane split;
    private final WindowChrome chrome;
    private final Region headerSeam = dividerSeam("workspace-header-seam");
    private final Region headerSeamRight = dividerSeam("workspace-header-seam-right");
    private final Region sidebarSeam = dividerSeam("workspace-sidebar-seam");
    private final Region outlineSeam = dividerSeam("workspace-outline-seam");
    private final ChangeListener<Bounds> outlineBounds = (ignored, before, after) -> positionSeams();
    private final ChangeListener<Transform> outlinePosition = (ignored, before, after) -> positionSeams();
    private final ChangeListener<Bounds> tabBounds = (ignored, before, after) -> positionSeams();
    private final ChangeListener<Transform> tabPosition = (ignored, before, after) -> positionSeams();
    private Node currentTab;
    private Region currentOutline;
    private boolean sidebarDividerActive;
    private boolean outlineDividerActive;
    private final WorkspaceNavigationService navigation;
    private final QuizForgeNavigationLinkCodec navigationCodec = new QuizForgeNavigationLinkCodec();
    private Workspace current;
    private double sidebarWidth = 250;

    MainWorkspaceView(WorkspaceService workspaces, WorkspaceFileService files, WorkspaceHistory history,
            FilePresentationLoader loader, QuestionBankReferenceResolver references, Stage stage,
            Runnable settings, BiConsumer<WorkspaceId, FilePresentation> ai,
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
        filePane = new FilePane(loader, ai, bankEdits,
                markdownEdits, registration, this::refreshTree, clipboard, sourceLinks, this::openNavigationUri,
                sourceNavigation, practice);
        tabs = new WorkspaceTabManager(() -> new FilePane(loader, ai, bankEdits,
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
            @Override public void copyLink(WorkspaceFileEntry entry) {
                tabs.activePane().copyAssetLink(current.id(), entry);
            }
        }, settings, this::refresh, () -> createFolder(""), type -> createFile("", type));
        tabs.setMinWidth(320);
        split = new SplitPane(sidebar, tabs);
        split.setId("workspace-split");
        split.getStyleClass().add("workspace-split");
        SplitPane.setResizableWithParent(sidebar, false);
        split.widthProperty().addListener((obs, before, width) -> {
            if (before.doubleValue() == 0 && width.doubleValue() > 0) {
                split.setDividerPositions(250 / width.doubleValue());
            }
        });
        chrome = new WindowChrome(stage, tabs.tabBar(), sidebar, this::toggleFileList);
        tabs.onActivate(path -> {
            trackTabSeam(tabs.activeTabNode());
            if (path != null) {
                var item = find(sidebar.tree().getRoot(), path);
                if (item != null) sidebar.tree().selectWithoutOpening(item);
            }
            Region outline = tabs.activePane().visibleOutline();
            chrome.trackOutline(outline);
            trackOutlineSeam(outline);
            Platform.runLater(this::positionSeams);
        });
        setTop(chrome);
        setCenter(split);
        outlineSeam.setVisible(false);
        getChildren().addAll(headerSeam, headerSeamRight, sidebarSeam, outlineSeam);
        sidebar.widthProperty().addListener((ignored, before, after) -> positionSeams());
        tabs.tabBar().hvalueProperty().addListener((ignored, before, after) -> positionSeams());
        widthProperty().addListener((ignored, before, after) -> positionSeams());
        heightProperty().addListener((ignored, before, after) -> positionSeams());
        sceneProperty().addListener((ignored, before, after) -> Platform.runLater(this::positionSeams));
        addEventFilter(MouseEvent.MOUSE_MOVED, this::updateDividerEmphasis);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, this::updateDividerEmphasis);
        addEventFilter(MouseEvent.MOUSE_PRESSED, this::updateDividerEmphasis);
        addEventFilter(MouseEvent.MOUSE_RELEASED, this::updateDividerEmphasis);
        addEventFilter(MouseEvent.MOUSE_EXITED, event -> setDividerEmphasis(false, false));
        var available = history.order(workspaces.listWorkspaces());
        if (!available.isEmpty()) switchWorkspace(available.getFirst());
        else {
            updateSwitcher();
            filePane.setCenter(UiTheme.quietState("欢迎使用 QuizForge", "在左上角打开或新建一个工作区。"));
        }
    }

    private void toggleFileList() {
        if (split.getItems().contains(sidebar)) {
            if (sidebar.getWidth() > 0) sidebarWidth = sidebar.getWidth();
            split.getItems().remove(sidebar);
            chrome.setFileListVisible(false);
            sidebarSeam.setVisible(false);
            setDividerEmphasis(false, outlineDividerActive);
        } else {
            split.getItems().addFirst(sidebar);
            chrome.setFileListVisible(true);
            sidebarSeam.setVisible(true);
            Platform.runLater(() -> {
                if (split.getWidth() > 0)
                    split.setDividerPositions(Math.min(0.7, sidebarWidth / split.getWidth()));
                positionSeams();
            });
        }
    }

    @Override protected void layoutChildren() {
        super.layoutChildren();
        positionSeams();
    }

    private static Region dividerSeam(String id) {
        Region line = new Region();
        line.setId(id);
        line.getStyleClass().add("workspace-divider-seam");
        line.setManaged(false);
        line.setMouseTransparent(true);
        return line;
    }

    private void trackOutlineSeam(Region outline) {
        if (currentOutline != null) {
            currentOutline.boundsInParentProperty().removeListener(outlineBounds);
            currentOutline.localToSceneTransformProperty().removeListener(outlinePosition);
        }
        currentOutline = outline;
        outlineSeam.setVisible(outline != null);
        if (outline == null) setDividerEmphasis(sidebarDividerActive, false);
        if (outline != null) {
            outline.boundsInParentProperty().addListener(outlineBounds);
            outline.localToSceneTransformProperty().addListener(outlinePosition);
        }
        positionSeams();
    }

    private void trackTabSeam(Node tab) {
        if (currentTab != null) {
            currentTab.boundsInParentProperty().removeListener(tabBounds);
            currentTab.localToSceneTransformProperty().removeListener(tabPosition);
        }
        currentTab = tab;
        if (tab != null) {
            tab.boundsInParentProperty().addListener(tabBounds);
            tab.localToSceneTransformProperty().addListener(tabPosition);
        }
    }

    private void positionSeams() {
        if (getScene() == null || getHeight() <= 0) return;
        if (chrome.getHeight() > 0) {
            double y = chrome.getHeight() - DIVIDER_LINE_WIDTH;
            Node activeTab = tabs.activeTabNode();
            if (activeTab != null && activeTab.getScene() != null && activeTab.getBoundsInLocal().getWidth() > 0) {
                double barLeft = sceneToLocal(tabs.tabBar().localToScene(0, 0)).getX();
                double barRight = barLeft + tabs.tabBar().getWidth();
                Bounds tabBounds = activeTab.localToScene(activeTab.getBoundsInLocal());
                double tabLeft = sceneToLocal(tabBounds.getMinX(), 0).getX();
                double tabRight = sceneToLocal(tabBounds.getMaxX(), 0).getX();
                double gapLeft = Math.max(barLeft, Math.min(barRight, tabLeft));
                double gapRight = Math.max(gapLeft, Math.min(barRight, tabRight));
                headerSeam.resizeRelocate(0, y, gapLeft, DIVIDER_LINE_WIDTH);
                headerSeamRight.resizeRelocate(gapRight, y, getWidth() - gapRight, DIVIDER_LINE_WIDTH);
            } else {
                headerSeam.resizeRelocate(0, y, getWidth(), DIVIDER_LINE_WIDTH);
                headerSeamRight.resizeRelocate(getWidth(), y, 0, DIVIDER_LINE_WIDTH);
            }
        }
        if (sidebarSeam.isVisible() && sidebar.getParent() != null) {
            double offset = sidebarDividerActive ? 0 : DIVIDER_HANDLE_WIDTH - DIVIDER_LINE_WIDTH;
            double edge = sidebar.localToScene(sidebar.getWidth() + offset, 0).getX();
            sidebarSeam.resizeRelocate(sceneToLocal(edge, 0).getX(), 0,
                    sidebarDividerActive ? DIVIDER_HANDLE_WIDTH : DIVIDER_LINE_WIDTH, getHeight());
        }
        if (outlineSeam.isVisible() && currentOutline != null && currentOutline.getScene() != null) {
            double edge = currentOutline.localToScene(0, 0).getX() - DIVIDER_HANDLE_WIDTH;
            outlineSeam.resizeRelocate(sceneToLocal(edge, 0).getX(), 0,
                    outlineDividerActive ? DIVIDER_HANDLE_WIDTH : DIVIDER_LINE_WIDTH, getHeight());
        }
    }

    private void updateDividerEmphasis(MouseEvent event) {
        SplitPane owner = dividerOwner(event.getTarget());
        setDividerEmphasis(owner == split, owner != null && owner == outlinePane());
    }

    private SplitPane outlinePane() {
        for (Node parent = currentOutline; parent != null; parent = parent.getParent())
            if (parent instanceof SplitPane pane) return pane;
        return null;
    }

    private static SplitPane dividerOwner(Object target) {
        if (!(target instanceof Node node)) return null;
        for (Node child = node; child != null; child = child.getParent()) {
            if (!child.getStyleClass().contains("split-pane-divider")) continue;
            for (Node parent = child.getParent(); parent != null; parent = parent.getParent())
                if (parent instanceof SplitPane pane) return pane;
        }
        return null;
    }

    private void setDividerEmphasis(boolean sidebarActive, boolean outlineActive) {
        if (sidebarDividerActive == sidebarActive && outlineDividerActive == outlineActive) return;
        sidebarDividerActive = sidebarActive;
        outlineDividerActive = outlineActive;
        sidebarSeam.pseudoClassStateChanged(DIVIDER_ACTIVE, sidebarActive);
        outlineSeam.pseudoClassStateChanged(DIVIDER_ACTIVE, outlineActive);
        positionSeams();
    }

    void switchWorkspace(Workspace workspace) {
        if (tabs.hasUnsavedChanges() && !confirmDiscard()) return;
        tabs.closeAll();
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

    private void createFolder(String parent) {
        if (current == null) return;
        sidebar.tree().beginCreateFolder(parent, name -> {
            String path = files.createFolder(current.id(), parent, name);
            refreshTree();
            selectPath(path);
        });
    }

    private void createFile(String parent, WorkspaceFileType type) {
        if (current == null) return;
        sidebar.tree().beginCreateFile(parent, type, name -> {
            String path = files.createFile(current.id(), parent, name, type);
            refreshTree();
            selectPath(path);
        });
    }

    private void copyPath(String path, boolean absolute) {
        try {
            String value = absolute ? files.absolutePath(current.id(), path).toString() : path;
            copyText(value);
        } catch (RuntimeException error) { showFileError("无法复制文件路径", error); }
    }

    private void copyText(String value) {
        clipboard.write(value);
    }

    private void rename(WorkspaceFileEntry entry) {
        if (tabs.hasUnsavedChangesUnder(entry.relativePath()) && !confirmDiscard()) return;
        sidebar.tree().beginRename(entry, name -> {
            String active = activePath();
            boolean affectsActive = contains(entry.relativePath(), active);
            String renamed = files.rename(current.id(), entry.relativePath(), name);
            tabs.renameUnder(current.id(), entry.relativePath(), renamed);
            refreshTree();
            if (affectsActive) selectPath(renamed + active.substring(entry.relativePath().length()));
            else selectPath(renamed);
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
        if (tabs.hasUnsavedChangesUnder(entry.relativePath()) && !confirmDiscard()) return;
        try {
            files.delete(current.id(), entry.relativePath());
            tabs.closeUnder(entry.relativePath());
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
        String path = activePath();
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

    private void openWorkspaceFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择已有的 QuizForge 工作区文件夹");
        chooser.setInitialDirectory(workspaces.defaultWorkspaceParent().toFile());
        java.io.File folder = chooser.showDialog(stage);
        if (folder == null) return;
        try {
            switchWorkspace(workspaces.registerExistingWorkspace(folder.toPath()));
        } catch (RuntimeException error) {
            showFileError("无法打开工作区文件夹", error);
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
