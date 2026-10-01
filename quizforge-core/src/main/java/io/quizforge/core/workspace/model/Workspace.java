package io.quizforge.core.workspace.model;

import java.nio.file.Path;
import java.time.Instant;

public record Workspace(WorkspaceId id, String name, Instant createdAt, Instant updatedAt, Path rootPath) {
    public Workspace(WorkspaceId id, String name, Instant createdAt, Instant updatedAt) {
        this(id, name, createdAt, updatedAt, null);
    }
}
