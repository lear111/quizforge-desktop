package io.quizforge.core.port;

import io.quizforge.core.workspace.model.WorkspaceFileEntry;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.util.List;

public interface WorkspaceFileCatalog {
    List<WorkspaceFileEntry> list(WorkspaceId workspaceId);
    WorkspaceFileEntry inspect(WorkspaceId workspaceId, String relativePath);
    io.quizforge.core.question.model.QuestionBank readBank(WorkspaceId workspaceId, String relativePath);
    String readText(WorkspaceId workspaceId, String relativePath);
    default java.io.InputStream openResource(WorkspaceId workspaceId, String relativePath,
            io.quizforge.core.question.resource.QBankResource resource) throws java.io.IOException {
        throw new java.io.IOException("Resource is unavailable");
    }
}
