package io.quizforge.core.port;

import io.quizforge.core.workspace.model.WorkspaceFileType;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.nio.file.Path;

/** Mutations of user-owned files in a Workspace. Paths are workspace-relative. */
public interface WorkspaceFileOperations {
    String createFolder(WorkspaceId workspace, String parentPath, String name);
    String createFile(WorkspaceId workspace, String parentPath, String name, WorkspaceFileType type);
    String rename(WorkspaceId workspace, String relativePath, String name);
    void delete(WorkspaceId workspace, String relativePath);
    Path absolutePath(WorkspaceId workspace, String relativePath);
}
