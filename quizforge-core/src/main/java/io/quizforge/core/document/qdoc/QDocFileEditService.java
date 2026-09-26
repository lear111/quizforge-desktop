package io.quizforge.core.document.qdoc;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.FileDocumentStorage;
import io.quizforge.core.port.QDocCodec;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;

/** Validates, safely replaces and immediately reindexes an edited QDoc asset. */
public final class QDocFileEditService {
    private final WorkspaceService workspaces;
    private final FileDocumentStorage files;
    private final WorkspaceAssetScanner scanner;
    private final QDocCodec codec;
    private final DocumentSchemaValidator validator = new DocumentSchemaValidator();

    public QDocFileEditService(WorkspaceService workspaces, FileDocumentStorage files,
            WorkspaceAssetScanner scanner, QDocCodec codec) {
        this.workspaces = workspaces;
        this.files = files;
        this.scanner = scanner;
        this.codec = codec;
    }

    public Asset save(WorkspaceId workspace, String relativePath, String expectedContentId,
            QDocDocument edited) {
        workspaces.getWorkspace(workspace);
        if (relativePath == null || !relativePath.toLowerCase(java.util.Locale.ROOT).endsWith(".qdoc"))
            throw new IllegalArgumentException("Only QDoc files can be edited here");
        validator.validate(edited);
        String serialized = codec.write(edited);
        String revision = codec.contentId(edited);
        QDocDocument current = codec.parse(files.read(workspace, relativePath));
        if (!edited.id().equals(current.id())) throw new IllegalArgumentException("Document assetId cannot change");
        if (!codec.contentId(current).equals(expectedContentId))
            throw new IllegalStateException("Document changed outside the editor. Reopen it before saving.");
        try (FileDocumentStorage.StagedFile staged = files.stageReplace(workspace, relativePath, serialized)) {
            staged.publish();
            try {
                Asset asset = scanner.scan(workspace).stream()
                        .filter(item -> item.assetId().equals(edited.id())
                                && item.assetType() == AssetType.STANDARD_DOCUMENT)
                        .findFirst().orElseThrow(() -> new IllegalStateException("Saved QDoc was not indexed"));
                if (!relativePath.equals(asset.currentPath()) || !revision.equals(asset.contentId()))
                    throw new IllegalStateException("Asset Registry does not match saved QDoc");
                staged.complete();
                return asset;
            } catch (RuntimeException failure) {
                try {
                    staged.rollback();
                    scanner.scan(workspace);
                } catch (RuntimeException recovery) { failure.addSuppressed(recovery); }
                throw failure;
            }
        }
    }
}
