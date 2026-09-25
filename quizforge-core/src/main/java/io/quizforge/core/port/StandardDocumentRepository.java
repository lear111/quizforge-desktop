package io.quizforge.core.port;

import io.quizforge.core.document.StandardDocument;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.Optional;

public interface StandardDocumentRepository {
    Optional<StandardDocument> findByWorkspace(WorkspaceId workspaceId);

    void save(StandardDocument document);
}
