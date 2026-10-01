package io.quizforge.core.port;

import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.core.workspace.model.WorkspaceFolder;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.nio.file.Path;

public interface WorkspaceDirectoryStorage {
    Path defaultParent();

    /** Reads an existing workspace without creating or changing its files. */
    WorkspaceFolder inspectExisting(Path root);

    Path rootOf(WorkspaceId id);

    void create(Workspace workspace);

    /** Adds missing file-first metadata to an existing workspace without moving legacy assets. */
    void ensure(Workspace workspace);

    void deleteIfEmpty(Workspace workspace);
}
