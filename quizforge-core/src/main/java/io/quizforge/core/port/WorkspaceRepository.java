package io.quizforge.core.port;

import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {
    void save(Workspace workspace);

    List<Workspace> list();

    Optional<Workspace> findById(WorkspaceId id);
}
