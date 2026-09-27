package io.quizforge.desktop.ui;

import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
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
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

final class WorkspaceFileTree extends TreeView<WorkspaceFileEntry> {
    interface FileActions {
        void createFolder(String parentPath);
        void createFile(String parentPath, io.quizforge.core.workspace.WorkspaceFileType type);
        void copyPath(String path, boolean absolute);
        void rename(WorkspaceFileEntry entry);
        void delete(WorkspaceFileEntry entry);
    }

    private final FileActions actions;

    WorkspaceFileTree(Consumer<WorkspaceFileEntry> open, FileActions actions) {
        this.actions = actions;
        setId("workspace-file-tree");
        setShowRoot(false);
        setCellFactory(ignored -> new FileCell());
        getSelectionModel().selectedItemProperty().addListener((obs, before, selected) -> {
            if (selected != null && selected.getValue() != null
                    && selected.getValue().kind() != WorkspaceFileKind.DIRECTORY) open.accept(selected.getValue());
        });
    }

    private final class FileCell extends TreeCell<WorkspaceFileEntry> {
        private final SVGPath arrow = new SVGPath();
        private final Map<String, Node> icons = new HashMap<>();
        private final Tooltip filename = new Tooltip();

        FileCell() {
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
            addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if (!isEmpty() && getContextMenu() != null) {
                    getContextMenu().show(this, event.getScreenX(), event.getScreenY());
                    event.consume();
                }
            });
        }

        private boolean folderClick(MouseEvent event) {
            return event.getButton() == MouseButton.PRIMARY && !isEmpty() && getTreeItem() != null
                    && getItem() != null && getItem().kind() == WorkspaceFileKind.DIRECTORY;
        }
        @Override protected void updateItem(WorkspaceFileEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null); setGraphic(null); setTooltip(null); setContextMenu(null); return;
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
            setGraphic(icons.computeIfAbsent(icon, UiTheme::icon));
            setContextMenu(menu(entry));
        }

        private ContextMenu menu(WorkspaceFileEntry entry) {
            ContextMenu menu = new ContextMenu();
            if (entry.kind() == WorkspaceFileKind.DIRECTORY) {
                menu.getItems().add(action("新建文件夹", "folder-new-folder",
                        () -> actions.createFolder(entry.relativePath())));
                menu.getItems().add(action("新建 .md 文件", "folder-new-md",
                        () -> actions.createFile(entry.relativePath(),
                                io.quizforge.core.workspace.WorkspaceFileType.MARKDOWN)));
                menu.getItems().add(action("新建 .qbank 文件", "folder-new-qbank",
                        () -> actions.createFile(entry.relativePath(),
                                io.quizforge.core.workspace.WorkspaceFileType.QUESTION_BANK)));
                menu.getItems().add(action("新建 .qdoc 文件", "folder-new-qdoc",
                        () -> actions.createFile(entry.relativePath(),
                                io.quizforge.core.workspace.WorkspaceFileType.QDOC)));
                menu.getItems().add(new SeparatorMenuItem());
            }
            Menu copy = new Menu("复制文件路径");
            copy.setId("copy-file-path");
            copy.getItems().addAll(action("复制相对路径", "copy-relative-path",
                    () -> actions.copyPath(entry.relativePath(), false)),
                    action("复制绝对路径", "copy-absolute-path",
                            () -> actions.copyPath(entry.relativePath(), true)));
            menu.getItems().addAll(copy, new SeparatorMenuItem(),
                    action("重命名", "rename-file-entry", () -> actions.rename(entry)),
                    action("删除", "delete-file-entry", () -> actions.delete(entry)));
            return menu;
        }

        private MenuItem action(String text, String id, Runnable run) {
            MenuItem item = new MenuItem(text);
            item.setId(id);
            item.setOnAction(event -> run.run());
            return item;
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
