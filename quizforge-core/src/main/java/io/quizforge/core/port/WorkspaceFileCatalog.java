package io.quizforge.core.port;

import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;

public interface WorkspaceFileCatalog {
    List<WorkspaceFileEntry> list(WorkspaceId workspaceId);
    WorkspaceFileEntry inspect(WorkspaceId workspaceId, String relativePath);
    io.quizforge.core.question.QuestionBank readBank(WorkspaceId workspaceId, String relativePath);
    String readText(WorkspaceId workspaceId, String relativePath);
}
