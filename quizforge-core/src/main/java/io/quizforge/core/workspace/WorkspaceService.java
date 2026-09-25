package io.quizforge.core.workspace;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.WorkspaceDirectoryStorage;
import io.quizforge.core.port.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
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
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new QuizForgeException(ErrorCode.INVALID_WORKSPACE_NAME, "Workspace name cannot be empty.");
        }
        Instant now = clock.instant();
        Workspace workspace = new Workspace(WorkspaceId.newId(), trimmed, now, now);
        boolean directoryCreated = false;
        try {
            directories.create(workspace);
            directoryCreated = true;
            repository.save(workspace);
        } catch (RuntimeException failure) {
            if (directoryCreated) {
                try {
                    directories.deleteIfEmpty(workspace.id());
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
