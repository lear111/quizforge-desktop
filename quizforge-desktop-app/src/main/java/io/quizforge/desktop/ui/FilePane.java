package io.quizforge.desktop.ui;

import io.quizforge.core.document.qdoc.QDocFileEditService;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.function.BiConsumer;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;

final class FilePane extends BorderPane {
    private final FilePresentationLoader loader;
    private final QuestionBankReferenceResolver references;
    private final BiConsumer<WorkspaceId, FilePresentation> aiAction;
    private final QDocFileEditService qdocEdits;
    private final FileViewerRouter router = new FileViewerRouter();
    private final AssetDetailsPopover details = new AssetDetailsPopover();
    private WorkspaceId workspace;
    private FilePresentation current;
    private FileMode mode = FileMode.BROWSE;
    private FileHeader header;
    private Node browseContent;
    private QDocEditorView qdocEditor;

    FilePane(FilePresentationLoader loader, QuestionBankReferenceResolver references,
            BiConsumer<WorkspaceId, FilePresentation> aiAction, QDocFileEditService qdocEdits) {
        this.loader = loader;
        this.references = references;
        this.aiAction = aiAction;
        this.qdocEdits = qdocEdits;
        setId("file-pane");
        clear();
    }

    void open(WorkspaceId workspace, String path) {
        clear();
        this.workspace = workspace;
        try {
            current = loader.load(workspace, path);
            header = new FileHeader(current, this::toggleMode, anchor -> {
                try {
                    // Re-read the actual file and recompute revisions/references on every open.
                    var fresh = loader.load(this.workspace, current.file().entry().relativePath());
                    if (!fresh.asset()) { details.showFailure(anchor); return; }
                    details.show(anchor, fresh, this.workspace, references);
                } catch (RuntimeException error) { details.showFailure(anchor); }
            }, this::invokeAi);
            setTop(header);
            browseContent = router.view(current, FileMode.BROWSE);
            setCenter(browseContent);
        } catch (RuntimeException error) {
            current = null;
            setTop(null);
            setCenter(UiTheme.quietState("无法打开文件", "文件可能已移动或无法读取，请刷新工作区。"));
        }
    }

    private void toggleMode() {
        details.hide();
        if (mode == FileMode.EDIT && qdocEditor != null && qdocEditor.dirty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Unsaved QDoc changes will be discarded.", ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Leave edit mode?");
            UiTheme.apply(confirm);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            open(workspace, current.file().entry().relativePath());
            return;
        }
        mode = mode == FileMode.BROWSE ? FileMode.EDIT : FileMode.BROWSE;
        header.updateMode(mode);
        if (mode == FileMode.EDIT && current.document() != null) {
            qdocEditor = new QDocEditorView(current.document(), this::saveQDoc, this::invokeAi);
            StackPane centered = new StackPane(qdocEditor);
            centered.setAlignment(Pos.TOP_CENTER);
            setCenter(UiTheme.scroll(centered));
        } else setCenter(mode == FileMode.BROWSE ? browseContent : router.view(current, mode));
    }

    private void saveQDoc(io.quizforge.core.document.qdoc.QDocDocument edited) {
        String path = current.file().entry().relativePath();
        qdocEdits.save(workspace, path, current.file().entry().contentId(), edited);
        open(workspace, path);
    }

    private void invokeAi() {
        if (qdocEditor != null && qdocEditor.dirty()) {
            Alert warning = new Alert(Alert.AlertType.INFORMATION,
                    "Save or leave edit mode before generating content.", ButtonType.OK);
            UiTheme.apply(warning);
            warning.showAndWait();
            return;
        }
        try {
            FilePresentation fresh = loader.load(workspace, current.file().entry().relativePath());
            if (fresh.offersAi()) aiAction.accept(workspace, fresh);
            else open(workspace, current.file().entry().relativePath());
        } catch (RuntimeException error) {
            open(workspace, current.file().entry().relativePath());
        }
    }

    void clear() {
        details.hide();
        current = null;
        workspace = null;
        browseContent = null;
        qdocEditor = null;
        mode = FileMode.BROWSE;
        setTop(null);
        setCenter(router.welcome());
    }

    FilePresentation currentFile() { return current; }
    FileMode mode() { return mode; }
    AssetDetailsPopover details() { return details; }
}
