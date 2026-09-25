package io.quizforge.core.port;

import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceId;

public interface WorkspaceDirectoryStorage {
    void create(Workspace workspace);

    /** Adds missing file-first metadata to an existing workspace without moving legacy assets. */
    void ensure(Workspace workspace);

    void deleteIfEmpty(WorkspaceId id);
}
