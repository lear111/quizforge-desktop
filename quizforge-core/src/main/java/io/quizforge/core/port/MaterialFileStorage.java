package io.quizforge.core.port;

import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.workspace.WorkspaceId;

public interface MaterialFileStorage {
    StoredMaterialFile store(WorkspaceId workspaceId, MaterialId materialId, String sourceFile, long maxBytes);

    String read(Material material);

    void delete(Material material);
}
