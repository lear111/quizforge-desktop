package io.quizforge.core.workspace;

import java.util.UUID;

public record WorkspaceId(UUID value) {
    public WorkspaceId {
        if (value == null) {
            throw new IllegalArgumentException("Workspace ID is required");
        }
    }

    public static WorkspaceId newId() {
        return new WorkspaceId(UUID.randomUUID());
    }

    public static WorkspaceId parse(String value) {
        return new WorkspaceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
