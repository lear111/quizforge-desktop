package io.quizforge.core.port;

import io.quizforge.core.question.SourceDocumentSnapshot;
import io.quizforge.core.workspace.WorkspaceId;

public interface FormalDocumentReader {
    SourceDocumentSnapshot read(WorkspaceId workspaceId, String relativePath);
}
