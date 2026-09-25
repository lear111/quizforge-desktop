package io.quizforge.core.workspace;

import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.port.WorkspaceFileCatalog;

/** File-first use cases for the desktop shell; asset scanning only enriches the filesystem view. */
public final class WorkspaceFileService {
    private final WorkspaceService workspaces;
    private final WorkspaceAssetScanner scanner;
    private final WorkspaceFileCatalog catalog;
    private final QuestionBankFileCodec banks;

    public WorkspaceFileService(WorkspaceService workspaces, WorkspaceAssetScanner scanner,
            WorkspaceFileCatalog catalog, QuestionBankFileCodec banks) {
        this.workspaces = workspaces;
        this.scanner = scanner;
        this.catalog = catalog;
        this.banks = banks;
    }

    public WorkspaceFileTree refresh(WorkspaceId workspaceId) {
        workspaces.getWorkspace(workspaceId);
        String warning = null;
        try { scanner.scanWithReport(workspaceId); }
        catch (RuntimeException error) { warning = "Asset Registry refresh failed: " + error.getMessage(); }
        return new WorkspaceFileTree(catalog.list(workspaceId), warning);
    }

    public OpenedWorkspaceFile open(WorkspaceId workspaceId, String relativePath) {
        workspaces.getWorkspace(workspaceId);
        WorkspaceFileEntry entry = catalog.inspect(workspaceId, relativePath);
        return switch (entry.kind()) {
            case MARKDOWN, STANDARD_DOCUMENT -> new OpenedWorkspaceFile(entry,
                    catalog.readText(workspaceId, relativePath), null);
            case QUESTION_BANK -> {
                String source = catalog.readText(workspaceId, relativePath);
                yield new OpenedWorkspaceFile(entry, source, banks.parse(source));
            }
            default -> new OpenedWorkspaceFile(entry, null, null);
        };
    }
}
