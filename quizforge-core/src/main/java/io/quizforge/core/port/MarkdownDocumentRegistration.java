package io.quizforge.core.port;

import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.document.registered.RegisteredMarkdownDocument;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.util.Optional;

public interface MarkdownDocumentRegistration {
    Optional<RegisteredMarkdownDocument> inspect(WorkspaceId workspaceId, String relativePath);

    /** Registers document identity without adding node IDs or source anchors. */
    RegisteredMarkdownDocument registerDocument(WorkspaceId workspaceId, String relativePath,
            String expectedSource);

    /** Adds metadata and one named anchor only when the user requests a source reference. */
    NamedMarkdownAnchor createAnchor(WorkspaceId workspaceId, String relativePath,
            String expectedSource, int bodyLine, int bodyColumn, String anchorName);
}
