package io.quizforge.core.workspace.service;

import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.port.WorkspaceFileOperations;
import io.quizforge.core.workspace.model.OpenedWorkspaceFile;
import io.quizforge.core.workspace.model.WorkspaceFileEntry;
import io.quizforge.core.workspace.model.WorkspaceFileTree;
import io.quizforge.core.workspace.model.WorkspaceFileType;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.nio.file.Path;

/** File-first use cases for the desktop shell; asset scanning only enriches the filesystem view. */
public final class WorkspaceFileService {
    private final WorkspaceService workspaces;
    private final WorkspaceAssetScanner scanner;
    private final WorkspaceFileCatalog catalog;
    private final QuestionBankFileCodec banks;
    private final WorkspaceFileOperations operations;

    public WorkspaceFileService(WorkspaceService workspaces, WorkspaceAssetScanner scanner,
            WorkspaceFileCatalog catalog, QuestionBankFileCodec banks,
            WorkspaceFileOperations operations) {
        this.workspaces = workspaces;
        this.scanner = scanner;
        this.catalog = catalog;
        this.banks = banks;
        this.operations = operations;
    }

    public String createFolder(WorkspaceId workspaceId, String parentPath, String name) {
        workspaces.getWorkspace(workspaceId);
        return operations.createFolder(workspaceId, parentPath, name);
    }

    public String createFile(WorkspaceId workspaceId, String parentPath, String name,
            WorkspaceFileType type) {
        workspaces.getWorkspace(workspaceId);
        return operations.createFile(workspaceId, parentPath, name, type);
    }

    public String rename(WorkspaceId workspaceId, String relativePath, String name) {
        workspaces.getWorkspace(workspaceId);
        return operations.rename(workspaceId, relativePath, name);
    }

    public void delete(WorkspaceId workspaceId, String relativePath) {
        workspaces.getWorkspace(workspaceId);
        operations.delete(workspaceId, relativePath);
    }

    public Path absolutePath(WorkspaceId workspaceId, String relativePath) {
        workspaces.getWorkspace(workspaceId);
        return operations.absolutePath(workspaceId, relativePath);
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
            case MARKDOWN, REGISTERED_MARKDOWN -> new OpenedWorkspaceFile(entry,
                    catalog.readText(workspaceId, relativePath), null);
            case INVALID_REGISTERED_MARKDOWN -> new OpenedWorkspaceFile(entry,
                    relativePath.toLowerCase(java.util.Locale.ROOT).endsWith(".md")
                            ? catalog.readText(workspaceId, relativePath) : null, null);
            case QUESTION_BANK -> {
                var bank = catalog.readBank(workspaceId, relativePath);
                banks.validate(bank);
                yield new OpenedWorkspaceFile(entry, null, bank, banks.contentId(bank));
            }
            default -> new OpenedWorkspaceFile(entry, null, null);
        };
    }
}
