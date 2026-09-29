package io.quizforge.desktop.ui;

import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceFileType;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.animation.Interpolator;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.css.PseudoClass;
import javafx.scene.input.KeyCode;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

final class WorkspaceFileTree extends TreeView<WorkspaceFileEntry> {
    interface FileActions {
        void createFolder(String parentPath);
        void createFile(String parentPath, io.quizforge.core.workspace.WorkspaceFileType type);
        void copyPath(String path, boolean absolute);
        void rename(WorkspaceFileEntry entry);
        void delete(WorkspaceFileEntry entry);
        void copyLink(WorkspaceFileEntry entry);
    }

    private final FileActions actions;
    private final BiConsumer<WorkspaceFileEntry, Boolean> open;
    private boolean suppressOpen;
    private PendingItem pending;
    private RenameItem renaming;
    private static final PseudoClass INVALID_NAME = PseudoClass.getPseudoClass("invalid-name");

    private static boolean isLinkableMarkdown(WorkspaceFileEntry entry) {
        return (entry.kind() == WorkspaceFileKind.MARKDOWN
                || entry.kind() == WorkspaceFileKind.STANDARD_DOCUMENT)
                && entry.relativePath().toLowerCase(java.util.Locale.ROOT).endsWith(".md");
    }

    WorkspaceFileTree(BiConsumer<WorkspaceFileEntry, Boolean> open, FileActions actions) {
        this.actions = actions;
        this.open = open;
        setId("workspace-file-tree");
        getStyleClass().add("workspace-file-tree");
        setShowRoot(false);
        setCellFactory(ignored -> new FileCell());
        getSelectionModel().selectedItemProperty().addListener((obs, before, selected) -> {
            if (!suppressOpen && selected != null && !(selected instanceof PendingItem)
                    && (renaming == null || selected != renaming.item)
                    && selected.getValue() != null
                    && selected.getValue().kind() != WorkspaceFileKind.DIRECTORY) open.accept(selected.getValue(), false);
        });
    }

    void selectWithoutOpening(TreeItem<WorkspaceFileEntry> item) {
        suppressOpen = true;
        try { getSelectionModel().select(item); }
        finally { suppressOpen = false; }
    }

    void beginCreateFile(String parentPath, WorkspaceFileType type, Consumer<String> create) {
        beginCreate(parentPath, type, create);
    }

    void beginCreateFolder(String parentPath, Consumer<String> create) {
        beginCreate(parentPath, null, create);
    }

    private void beginCreate(String parentPath, WorkspaceFileType type, Consumer<String> create) {
        cancelRename();
        cancelPending();
        TreeItem<WorkspaceFileEntry> parent = folderItem(getRoot(), parentPath);
        if (parent == null) throw new IllegalArgumentException("Choose an existing folder");
        PendingItem item = new PendingItem(parentPath, type, create);
        pending = item;
        parent.setExpanded(true);
        parent.getChildren().add(0, item);
        selectWithoutOpening(item);
        scrollTo(getRow(item));
        Platform.runLater(() -> { if (pending == item) item.name.focus(); });
    }

    void beginRename(WorkspaceFileEntry entry, Consumer<String> rename) {
        cancelPending();
        cancelRename();
        TreeItem<WorkspaceFileEntry> item = treeItem(getRoot(), entry.relativePath());
        if (item == null) throw new IllegalArgumentException("File or folder no longer exists");
        RenameItem edit = new RenameItem(item, rename);
        renaming = edit;
        selectWithoutOpening(item);
        scrollTo(getRow(item));
        refresh();
        Platform.runLater(() -> { if (renaming == edit) edit.focus(); });
    }

    private TreeItem<WorkspaceFileEntry> treeItem(TreeItem<WorkspaceFileEntry> node, String path) {
        if (node == null) return null;
        for (TreeItem<WorkspaceFileEntry> child : node.getChildren()) {
            if (child.getValue() != null && child.getValue().relativePath().equals(path)) return child;
            TreeItem<WorkspaceFileEntry> found = treeItem(child, path);
            if (found != null) return found;
        }
        return null;
    }

    private TreeItem<WorkspaceFileEntry> folderItem(TreeItem<WorkspaceFileEntry> node, String path) {
        if (node == null) return null;
        if (path == null || path.isEmpty()) return node;
        for (TreeItem<WorkspaceFileEntry> child : node.getChildren()) {
            WorkspaceFileEntry entry = child.getValue();
            if (entry != null && entry.kind() == WorkspaceFileKind.DIRECTORY) {
                if (entry.relativePath().equals(path)) return child;
                if (path.startsWith(entry.relativePath() + "/")) {
                    TreeItem<WorkspaceFileEntry> found = folderItem(child, path);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private void cancelPending() {
        PendingItem item = pending;
        pending = null;
        if (item != null && item.getParent() != null) item.getParent().getChildren().remove(item);
    }

    private void commitPending(PendingItem item) {
        if (pending != item) return;
        String name = item.name.field.getText();
        if (name == null || name.isBlank()) { cancelPending(); return; }
        String actualName = item.type == null ? name : fileName(name, item.type);
        if (duplicate(item.getParent(), item, actualName)) {
            item.name.error("当前文件夹中已存在同名文件或文件夹"); return;
        }
        try {
            item.create.accept(name);
            if (pending == item) cancelPending();
        } catch (RuntimeException error) {
            item.name.error(error.getMessage() == null ? "无法新建" : error.getMessage());
        }
    }

    private void cancelRename() {
        if (renaming == null) return;
        renaming = null;
        refresh();
    }

    private void commitRename(RenameItem edit) {
        if (renaming != edit) return;
        String name = edit.name.field.getText();
        if (name == null || name.isBlank() || name.equals(edit.item.getValue().name())) {
            cancelRename(); return;
        }
        if (duplicate(edit.item.getParent(), edit.item, name)) {
            edit.name.error("当前文件夹中已存在同名文件或文件夹"); return;
        }
        try {
            edit.rename.accept(name);
            if (renaming == edit) cancelRename();
        } catch (RuntimeException error) {
            edit.name.error(error.getMessage() == null ? "无法重命名" : error.getMessage());
        }
    }

    private boolean duplicate(TreeItem<WorkspaceFileEntry> parent, TreeItem<WorkspaceFileEntry> self, String name) {
        return parent.getChildren().stream().filter(sibling -> sibling != self && sibling.getValue() != null)
                .anyMatch(sibling -> sibling.getValue().name().equalsIgnoreCase(name));
    }

    private String fileName(String name, WorkspaceFileType type) {
        return name.toLowerCase(Locale.ROOT).endsWith(type.extension()) ? name : name + type.extension();
    }

    private final class PendingItem extends TreeItem<WorkspaceFileEntry> {
        private final WorkspaceFileType type;
        private final Consumer<String> create;
        private final InlineName name;

        PendingItem(String parentPath, WorkspaceFileType type, Consumer<String> create) {
            super(new WorkspaceFileEntry((parentPath.isEmpty() ? "" : parentPath + "/")
                    + ".__quizforge_new_entry__" + (type == null ? "" : type.extension()),
                    type == null ? "新建文件夹" : "新建文件" + type.extension(),
                    type == null ? WorkspaceFileKind.DIRECTORY
                            : type == WorkspaceFileType.MARKDOWN ? WorkspaceFileKind.MARKDOWN : WorkspaceFileKind.QUESTION_BANK,
                    null, null, null, null));
            this.type = type;
            this.create = create;
            name = new InlineName(type == null ? "inline-new-folder-name" : "inline-new-file-name",
                    type == null ? "folder" : type == WorkspaceFileType.MARKDOWN ? "markdown" : "qbank",
                    type == null ? "文件夹名称" : "文件名" + type.extension(), "",
                    () -> commitPending(this), WorkspaceFileTree.this::cancelPending);
        }
    }

    private final class RenameItem {
        private final TreeItem<WorkspaceFileEntry> item;
        private final Consumer<String> rename;
        private final InlineName name;

        RenameItem(TreeItem<WorkspaceFileEntry> item, Consumer<String> rename) {
            this.item = item;
            this.rename = rename;
            WorkspaceFileEntry entry = item.getValue();
            String icon = switch (entry.kind()) {
                case DIRECTORY -> "folder";
                case QUESTION_BANK, INVALID_QUESTION_BANK -> "qbank";
                default -> "markdown";
            };
            name = new InlineName("inline-rename-name", icon, "新名称", entry.name(),
                    () -> commitRename(this), WorkspaceFileTree.this::cancelRename);
        }

        void focus() {
            name.field.requestFocus();
            String fileName = item.getValue().name();
            String lower = fileName.toLowerCase(Locale.ROOT);
            int extensionLength = lower.endsWith(".qbank") ? 6 : lower.endsWith(".md") ? 3 : 0;
            if (item.getValue().kind() == WorkspaceFileKind.DIRECTORY || extensionLength == 0)
                name.field.selectAll();
            else name.field.selectRange(0, fileName.length() - extensionLength);
        }
    }

    private final class InlineName {
        private final TextField field = new TextField();
        private final HBox graphic;

        InlineName(String id, String icon, String prompt, String initial, Runnable commit, Runnable cancel) {
            field.setId(id);
            field.setPromptText(prompt);
            field.setText(initial);
            field.setMinWidth(0);
            field.setPrefWidth(150);
            field.setMaxWidth(Double.MAX_VALUE);
            field.textProperty().addListener((obs, before, after) -> {
                field.pseudoClassStateChanged(INVALID_NAME, false);
                field.setTooltip(null);
            });
            field.setOnAction(event -> commit.run());
            field.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) { cancel.run(); event.consume(); }
            });
            field.focusedProperty().addListener((obs, before, focused) -> {
                if (!focused) commit.run();
            });
            graphic = new HBox(6, UiTheme.workspaceTreeIcon(icon), field);
            graphic.getStyleClass().add("workspace-inline-name");
            HBox.setHgrow(field, Priority.ALWAYS);
        }

        void focus() { field.requestFocus(); field.selectAll(); }

        void error(String message) {
            field.pseudoClassStateChanged(INVALID_NAME, true);
            field.setTooltip(new Tooltip(message));
        }
    }

    private final class FileCell extends TreeCell<WorkspaceFileEntry> {
        private final SVGPath arrow = new SVGPath();
        private final Map<String, Node> icons = new HashMap<>();
        private final Tooltip filename = new Tooltip();

        FileCell() {
            getStyleClass().add("workspace-tree-row");
            setWrapText(false);
            // Let VirtualFlow fit rows to its viewport instead of measuring full filenames.
            // TreeCellSkin then ellipsizes the text, preserving indentation and vertical scrolling.
            setMinWidth(0);
            setPrefWidth(0);
            setTextOverrun(OverrunStyle.ELLIPSIS);
            arrow.setContent("M1 1 L5 5 L1 9");
            arrow.getStyleClass().add("folder-chevron");
            StackPane disclosure = new StackPane(arrow);
            disclosure.getStyleClass().add("folder-disclosure");
            setDisclosureNode(disclosure);
            treeItemProperty().addListener((obs, before, after) -> {
                arrow.rotateProperty().unbind();
                if (after instanceof FolderItem folder) arrow.rotateProperty().bind(folder.angle);
                else arrow.setRotate(0);
            });
            // Handle the whole folder row, including its disclosure node, exactly once per click.
            // Suppress the default TreeCell double-click behavior for folders only.
            addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getButton() == MouseButton.SECONDARY) {
                    // Opening the context menu must not switch away from an unsaved editor.
                    event.consume();
                    return;
                }
                if (!folderClick(event)) return;
                getTreeView().requestFocus();
                getTreeView().getSelectionModel().select(getTreeItem());
                getTreeView().getFocusModel().focus(getIndex());
                event.consume();
            });
            addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                if (event.getButton() == MouseButton.SECONDARY) {
                    event.consume();
                    return;
                }
                if (folderClick(event)) event.consume();
            });
            addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.SECONDARY) {
                    event.consume();
                    return;
                }
                if (!folderClick(event)) return;
                if (event.isStillSincePress()) getTreeItem().setExpanded(!getTreeItem().isExpanded());
                event.consume();
            });
            addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                        && !isEmpty() && !(getTreeItem() instanceof PendingItem)
                        && (renaming == null || getTreeItem() != renaming.item)
                        && getItem().kind() != WorkspaceFileKind.DIRECTORY)
                    open.accept(getItem(), true);
            });
            addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if (!isEmpty() && getContextMenu() != null) {
                    getContextMenu().show(this, event.getScreenX(), event.getScreenY());
                    event.consume();
                }
            });
        }

        private boolean folderClick(MouseEvent event) {
            return event.getButton() == MouseButton.PRIMARY && !isEmpty() && getTreeItem() != null
                    && !(getTreeItem() instanceof PendingItem)
                    && (renaming == null || getTreeItem() != renaming.item)
                    && getItem() != null && getItem().kind() == WorkspaceFileKind.DIRECTORY;
        }
        @Override protected void updateItem(WorkspaceFileEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null); setGraphic(null); setTooltip(null); setContextMenu(null); return;
            }
            if (getTreeItem() instanceof PendingItem pendingItem) {
                setText(null); setGraphic(pendingItem.name.graphic); setTooltip(null); setContextMenu(null); return;
            }
            if (renaming != null && getTreeItem() == renaming.item) {
                setText(null); setGraphic(renaming.name.graphic); setTooltip(null); setContextMenu(null); return;
            }
            filename.setText(entry.name());
            setTooltip(filename);
            setText(entry.name());
            String icon = switch (entry.kind()) {
                case DIRECTORY -> "folder";
                case QUESTION_BANK, INVALID_QUESTION_BANK -> "qbank";
                case MARKDOWN, STANDARD_DOCUMENT, INVALID_STANDARD_DOCUMENT -> "markdown";
                default -> "file";
            };
            // Expansion reconfigures virtualized cells. Reuse styled graphics rather than
            // inserting a fresh, temporarily unstyled SVG on every updateItem call.
            setGraphic(icons.computeIfAbsent(icon, UiTheme::workspaceTreeIcon));
            setContextMenu(menu(entry));
        }

        private ContextMenu menu(WorkspaceFileEntry entry) {
            ContextMenu menu = new ContextMenu();
            if (entry.kind() == WorkspaceFileKind.DIRECTORY) {
                menu.getItems().add(WorkspaceMenus.action("新建文件夹", "folder-new-folder", "folder-plus",
                        () -> actions.createFolder(entry.relativePath())));
                Menu files = WorkspaceMenus.submenu("新建文件", "folder-new-file", "file-plus");
                files.getItems().add(WorkspaceMenus.textAction("Markdown 文件 (.md)", "folder-new-md",
                        () -> actions.createFile(entry.relativePath(),
                                io.quizforge.core.workspace.WorkspaceFileType.MARKDOWN)));
                files.getItems().add(WorkspaceMenus.textAction("题库文件 (.qbank)", "folder-new-qbank",
                        () -> actions.createFile(entry.relativePath(),
                                io.quizforge.core.workspace.WorkspaceFileType.QUESTION_BANK)));
                menu.getItems().addAll(files, WorkspaceMenus.separator());
            }
            if (isLinkableMarkdown(entry)) {
                menu.getItems().add(WorkspaceMenus.action("复制链接", "copy-link", "link",
                        () -> actions.copyLink(entry)));
            }
            Menu copy = WorkspaceMenus.submenu("复制路径", "copy-file-path", "copy");
            copy.getItems().addAll(WorkspaceMenus.textAction("复制相对路径", "copy-relative-path",
                    () -> actions.copyPath(entry.relativePath(), false)),
                    WorkspaceMenus.textAction("复制绝对路径", "copy-absolute-path",
                            () -> actions.copyPath(entry.relativePath(), true)));
            MenuItem delete = WorkspaceMenus.action("删除", "delete-file-entry", "trash", () -> actions.delete(entry));
            delete.getStyleClass().add("workspace-menu-danger");
            menu.getItems().addAll(copy, WorkspaceMenus.separator(),
                    WorkspaceMenus.action("重命名", "rename-file-entry", "edit", () -> actions.rename(entry)), delete);
            return menu;
        }
    }

    /** Keep animation on the folder so virtualized cell replacement cannot interrupt it. */
    private static final class FolderItem extends TreeItem<WorkspaceFileEntry> {
        private final DoubleProperty angle = new SimpleDoubleProperty(90);
        private final Timeline rotation = new Timeline();

        FolderItem(WorkspaceFileEntry entry) {
            super(entry);
            setExpanded(true);
            expandedProperty().addListener((obs, before, after) -> {
                rotation.stop();
                rotation.getKeyFrames().setAll(
                        new KeyFrame(Duration.ZERO, new KeyValue(angle, angle.get())),
                        new KeyFrame(Duration.millis(160), new KeyValue(angle, after ? 90 : 0, Interpolator.EASE_BOTH)));
                rotation.playFromStart();
            });
        }

        @Override public boolean isLeaf() { return false; }
    }

    void replace(io.quizforge.core.workspace.WorkspaceFileTree snapshot) {
        pending = null;
        renaming = null;
        getSelectionModel().clearSelection();
        TreeItem<WorkspaceFileEntry> root = new TreeItem<>();
        root.setExpanded(true);
        snapshot.childrenOf("").forEach(entry -> root.getChildren().add(item(snapshot, entry)));
        setRoot(root);
    }

    private TreeItem<WorkspaceFileEntry> item(io.quizforge.core.workspace.WorkspaceFileTree snapshot, WorkspaceFileEntry entry) {
        // Empty folders remain real folder nodes, independent of default folder names.
        TreeItem<WorkspaceFileEntry> item = entry.kind() == WorkspaceFileKind.DIRECTORY
                ? new FolderItem(entry) : new TreeItem<>(entry);
        if (entry.kind() == WorkspaceFileKind.DIRECTORY) {
            snapshot.childrenOf(entry.relativePath()).forEach(child -> item.getChildren().add(item(snapshot, child)));
        }
        return item;
    }
}
