package io.quizforge.core.document;

import io.quizforge.core.material.MaterialId;
import io.quizforge.core.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;

public record StandardDocument(StandardDocumentId id, WorkspaceId workspaceId, String title,
        String formatId, String formatVersion, String fileName, StandardDocumentStatus status,
        Instant createdAt, Instant updatedAt, List<MaterialId> sourceMaterialIds) {
    public StandardDocument {
        sourceMaterialIds = List.copyOf(sourceMaterialIds);
    }
}
