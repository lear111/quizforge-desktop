package io.quizforge.core.workspace;

import java.time.Instant;

public record Workspace(WorkspaceId id, String name, Instant createdAt, Instant updatedAt) {
}
