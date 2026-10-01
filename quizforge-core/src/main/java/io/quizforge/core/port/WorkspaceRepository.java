package io.quizforge.core.port;

import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {
    void save(Workspace workspace);

    List<Workspace> list();

    Optional<Workspace> findById(WorkspaceId id);
}
