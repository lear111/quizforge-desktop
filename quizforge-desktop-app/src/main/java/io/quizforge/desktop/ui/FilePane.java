package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.function.BiConsumer;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

final class FilePane extends BorderPane {
    private final FilePresentationLoader loader;
    private final QuestionBankReferenceResolver references;
    private final BiConsumer<WorkspaceId, FilePresentation> aiAction;
    private final FileViewerRouter router = new FileViewerRouter();
    private final AssetDetailsPopover details = new AssetDetailsPopover();
    private WorkspaceId workspace;
    private FilePresentation current;
    private FileMode mode = FileMode.BROWSE;
    private FileHeader header;
    private Node browseContent;

    FilePane(FilePresentationLoader loader, QuestionBankReferenceResolver references,
            BiConsumer<WorkspaceId, FilePresentation> aiAction) {
        this.loader = loader;
        this.references = references;
        this.aiAction = aiAction;
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
        mode = mode == FileMode.BROWSE ? FileMode.EDIT : FileMode.BROWSE;
        header.updateMode(mode);
        setCenter(mode == FileMode.BROWSE ? browseContent : router.view(current, mode));
    }

    private void invokeAi() {
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
        mode = FileMode.BROWSE;
        setTop(null);
        setCenter(router.welcome());
    }

    FilePresentation currentFile() { return current; }
    FileMode mode() { return mode; }
    AssetDetailsPopover details() { return details; }
}
