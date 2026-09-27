package io.quizforge.core.port;

import io.quizforge.core.document.registered.RegisteredMarkdownDocument;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.Optional;

public interface MarkdownDocumentRegistration {
    RegisteredMarkdownDocument register(WorkspaceId workspaceId, String relativePath);

    RegisteredMarkdownDocument ensureAddressing(WorkspaceId workspaceId, String relativePath);

    Optional<RegisteredMarkdownDocument> inspect(WorkspaceId workspaceId, String relativePath);
}
