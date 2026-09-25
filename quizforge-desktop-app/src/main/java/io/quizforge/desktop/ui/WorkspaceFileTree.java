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
import javafx.animation.Interpolator;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

final class WorkspaceFileTree extends TreeView<WorkspaceFileEntry> {
    WorkspaceFileTree(Consumer<WorkspaceFileEntry> open) {
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
                if (!folderClick(event)) return;
                getTreeView().requestFocus();
                getTreeView().getSelectionModel().select(getTreeItem());
                getTreeView().getFocusModel().focus(getIndex());
                event.consume();
            });
            addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                if (folderClick(event)) event.consume();
            });
            addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (!folderClick(event)) return;
                if (event.isStillSincePress()) getTreeItem().setExpanded(!getTreeItem().isExpanded());
                event.consume();
            });
        }

        private boolean folderClick(MouseEvent event) {
            return event.getButton() == MouseButton.PRIMARY && !isEmpty() && getTreeItem() != null
                    && getItem() != null && getItem().kind() == WorkspaceFileKind.DIRECTORY;
        }
        @Override protected void updateItem(WorkspaceFileEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) { setText(null); setGraphic(null); setTooltip(null); return; }
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
