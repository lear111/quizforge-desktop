package io.quizforge.core.port;

import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;

public interface MaterialRepository {
    void save(Material material);

    List<Material> listByWorkspace(WorkspaceId workspaceId);

    Optional<Material> findById(MaterialId id);

    void delete(MaterialId id);
}
