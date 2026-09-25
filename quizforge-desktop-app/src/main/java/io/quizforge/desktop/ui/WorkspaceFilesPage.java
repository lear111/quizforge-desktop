package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceFileService;
import io.quizforge.core.workspace.WorkspaceFileTree;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** File tree on the left, routed read-only viewer on the right. */
final class WorkspaceFilesPage {
    private final Workspace workspace;
    private final WorkspaceFileService files;
    private final FileViewerRouter viewers;
    private final BorderPane shell = new BorderPane();
    private final TreeView<WorkspaceFileEntry> tree = new TreeView<>();
    private final StackPane viewer = new StackPane();
    private final Label status = UiTheme.label("", "muted");

    WorkspaceFilesPage(Workspace workspace, WorkspaceFileService files,
            QuestionBankReferenceResolver references, Runnable openSettings) {
        this.workspace = workspace;
        this.files = files;
        this.viewers = new FileViewerRouter(references);
        Button refresh = UiTheme.button("Refresh Files", "arrow", "quiet", this::refresh);
        refresh.setId("refresh-files");
        Button settings = UiTheme.button("Settings", "settings", "quiet", openSettings);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(12, UiTheme.label(workspace.name(), "section-title"), spacer,
                status, refresh, settings);
        toolbar.setPadding(new Insets(14, 18, 14, 18));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        shell.setTop(toolbar);
        tree.setId("workspace-file-tree");
        tree.setShowRoot(false);
        tree.setPrefWidth(300);
        tree.setMinWidth(180);
        tree.setCellFactory(ignored -> new TreeCell<>() {
            @Override protected void updateItem(WorkspaceFileEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                setText(item.name() + (item.kind() == WorkspaceFileKind.INVALID_QUESTION_BANK
                        || item.kind() == WorkspaceFileKind.INVALID_STANDARD_DOCUMENT ? "  ⚠" : ""));
                setGraphic(UiTheme.icon(item.kind() == WorkspaceFileKind.DIRECTORY ? "folder"
                        : item.kind() == WorkspaceFileKind.QUESTION_BANK ? "book" : "file"));
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((observed, oldItem, item) -> {
            if (item != null && item.getValue() != null) open(item.getValue());
        });
        viewer.setId("file-viewer");
        SplitPane split = new SplitPane(tree, viewer);
        split.setDividerPositions(0.29);
        shell.setCenter(split);
        refresh();
    }

    Node content() { return shell; }

    void refresh() {
        try {
            WorkspaceFileTree snapshot = files.refresh(workspace.id());
            TreeItem<WorkspaceFileEntry> root = new TreeItem<>();
            root.setExpanded(true);
            for (WorkspaceFileEntry entry : snapshot.childrenOf("")) {
                root.getChildren().add(item(snapshot, entry));
            }
            tree.setRoot(root);
            status.setText(snapshot.registryWarning() == null ? "" : "Registry needs attention");
            show(snapshot.hasFiles() ? viewers.welcome() : UiTheme.emptyState("folder",
                    "This workspace is empty", "Add a file or use the existing import and generation tools."));
        } catch (RuntimeException error) {
            status.setText("Refresh failed");
            show(UiTheme.label("Could not refresh Workspace files: " + error.getMessage(), "warning"));
        }
    }

    private TreeItem<WorkspaceFileEntry> item(WorkspaceFileTree snapshot, WorkspaceFileEntry entry) {
        TreeItem<WorkspaceFileEntry> item = new TreeItem<>(entry);
        if (entry.kind() == WorkspaceFileKind.DIRECTORY) {
            for (WorkspaceFileEntry child : snapshot.childrenOf(entry.relativePath())) {
                item.getChildren().add(item(snapshot, child));
            }
            item.setExpanded(true);
        }
        return item;
    }

    private void open(WorkspaceFileEntry entry) {
        try { show(viewers.view(workspace.id(), files.open(workspace.id(), entry.relativePath()))); }
        catch (RuntimeException error) {
            show(UiTheme.label("Could not open " + entry.relativePath() + ": " + error.getMessage(), "warning"));
        }
    }

    private void show(Node content) {
        viewer.getChildren().setAll(UiTheme.scroll(content));
    }
}
