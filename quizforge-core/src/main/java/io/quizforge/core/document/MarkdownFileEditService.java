package io.quizforge.core.document;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.FileDocumentStorage;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import java.util.Locale;
import java.util.Objects;

/** Safely saves the source of an existing Markdown file and refreshes the asset registry. */
public final class MarkdownFileEditService {
    private final WorkspaceService workspaces;
    private final FileDocumentStorage files;
    private final WorkspaceAssetScanner scanner;

    public MarkdownFileEditService(WorkspaceService workspaces, FileDocumentStorage files,
            WorkspaceAssetScanner scanner) {
        this.workspaces = workspaces;
        this.files = files;
        this.scanner = scanner;
    }

    public void save(WorkspaceId workspace, String relativePath, String openedSource,
            String editedSource, String expectedAssetId) {
        workspaces.getWorkspace(workspace);
        if (relativePath == null || !relativePath.toLowerCase(Locale.ROOT).endsWith(".md"))
            throw new IllegalArgumentException("Only .md files can be edited here");
        Objects.requireNonNull(openedSource, "openedSource");
        Objects.requireNonNull(editedSource, "editedSource");
        if (!openedSource.equals(files.read(workspace, relativePath)))
            throw new IllegalStateException("Markdown changed outside the editor. Reopen it before saving.");
        try (FileDocumentStorage.StagedFile staged = files.stageReplace(workspace, relativePath, editedSource)) {
            staged.publish();
            try {
                if (!editedSource.equals(files.read(workspace, relativePath)))
                    throw new IllegalStateException("Saved Markdown does not match the edited text");
                var assets = scanner.scan(workspace);
                if (expectedAssetId != null) {
                    Asset asset = assets.stream()
                            .filter(item -> expectedAssetId.equals(item.assetId())
                                    && item.assetType() == AssetType.STANDARD_DOCUMENT)
                            .findFirst().orElseThrow(() -> new IllegalStateException(
                                    "Edited StandardDocument is invalid or changed its assetId"));
                    if (!relativePath.equals(asset.currentPath()))
                        throw new IllegalStateException("Asset Registry does not match saved Markdown");
                }
                staged.complete();
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
