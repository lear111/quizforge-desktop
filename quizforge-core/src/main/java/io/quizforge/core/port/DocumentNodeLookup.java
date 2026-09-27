package io.quizforge.core.port;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.workspace.WorkspaceId;

/** Resolves an addressable node from the document file currently on disk. */
public interface DocumentNodeLookup {
    record Result(String contentId, boolean containsNode) { }
    Result lookup(WorkspaceId workspace, Asset document, String nodeId);
}
