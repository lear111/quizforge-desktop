package io.quizforge.core.workspace;

import java.time.Instant;
import java.nio.file.Path;

public record Workspace(WorkspaceId id, String name, Instant createdAt, Instant updatedAt, Path rootPath) {
    public Workspace(WorkspaceId id, String name, Instant createdAt, Instant updatedAt) {
        this(id, name, createdAt, updatedAt, null);
    }
}
