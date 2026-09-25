package io.quizforge.core.port;

import io.quizforge.core.workspace.WorkspaceId;

public interface WorkspaceDirectoryStorage {
    void create(WorkspaceId id);

    void deleteIfEmpty(WorkspaceId id);
}
