package io.quizforge.core.material;

import io.quizforge.core.workspace.WorkspaceId;
import java.time.Instant;

public record Material(
        MaterialId id,
        WorkspaceId workspaceId,
        String originalFileName,
        String storedFileName,
        long fileSize,
        MaterialStatus status,
        Instant createdAt) {
}
