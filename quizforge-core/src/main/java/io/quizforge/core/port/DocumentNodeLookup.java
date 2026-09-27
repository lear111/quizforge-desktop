package io.quizforge.core.port;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.workspace.WorkspaceId;

/** Resolves an addressable node from the document file currently on disk. */
public interface DocumentNodeLookup {
    record Result(String contentId, boolean containsNode) { }
    record AnchorResult(String contentId, boolean containsAnchor, boolean orphan) { }
    Result lookup(WorkspaceId workspace, Asset document, String nodeId);
    AnchorResult lookupAnchor(WorkspaceId workspace, Asset document, String anchorName, int occurrence);

    /** New Source links require an explicit qf:anchor marker, not a legacy qf:id. */
    default AnchorResult lookupNamedAnchor(WorkspaceId workspace, Asset document,
            String anchorName, int occurrence) {
        return lookupAnchor(workspace, document, anchorName, occurrence);
    }
}
