package io.quizforge.core.workspace;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.WorkspaceDirectoryStorage;
import io.quizforge.core.port.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;

public final class WorkspaceService {
    private final WorkspaceRepository repository;
    private final WorkspaceDirectoryStorage directories;
    private final Clock clock;

    public WorkspaceService(WorkspaceRepository repository, WorkspaceDirectoryStorage directories, Clock clock) {
        this.repository = repository;
        this.directories = directories;
        this.clock = clock;
    }

    public Workspace createWorkspace(String name) {
        return createWorkspace(name, null);
    }

    public Path defaultWorkspaceParent() {
        return directories.defaultParent();
    }

    /** Registers an existing folder; its manifest remains the source of workspace identity. */
    public Workspace registerExistingWorkspace(Path root) {
        WorkspaceFolder folder = directories.inspectExisting(root);
        var known = repository.findById(folder.id());
        if (known.isPresent()) {
            if (!directories.rootOf(folder.id()).equals(folder.rootPath())) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "This workspace ID is already registered at another folder.");
            }
            return known.get();
        }
        Instant now = clock.instant();
        Workspace workspace = new Workspace(folder.id(), folder.name(), now, now, folder.rootPath());
        repository.save(workspace);
        return workspace;
    }

    /** Creates a named workspace folder under the selected parent; null keeps the legacy default location. */
    public Workspace createWorkspace(String name, Path parentDirectory) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new QuizForgeException(ErrorCode.INVALID_WORKSPACE_NAME, "Workspace name cannot be empty.");
        }
        Path rootPath = null;
        if (parentDirectory != null) {
            if (!trimmed.equals(name) || trimmed.equals(".") || trimmed.equals("..")
                    || trimmed.endsWith(".") || trimmed.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*")
                    || trimmed.matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
                throw new QuizForgeException(ErrorCode.INVALID_WORKSPACE_NAME, "Invalid workspace folder name.");
            }
            rootPath = parentDirectory.toAbsolutePath().normalize().resolve(trimmed);
        }
        Instant now = clock.instant();
        Workspace workspace = new Workspace(WorkspaceId.newId(), trimmed, now, now, rootPath);
        boolean directoryCreated = false;
        try {
            directories.create(workspace);
            directoryCreated = true;
            repository.save(workspace);
        } catch (RuntimeException failure) {
            if (directoryCreated) {
                try {
                    directories.deleteIfEmpty(workspace);
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        return workspace;
    }

    public List<Workspace> listWorkspaces() {
        List<Workspace> workspaces = repository.list();
        workspaces.forEach(directories::ensure);
        return workspaces;
    }

    public Workspace getWorkspace(WorkspaceId id) {
        Workspace workspace = repository.findById(id).orElseThrow(() ->
                new QuizForgeException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace was not found."));
        directories.ensure(workspace);
        return workspace;
    }
}
