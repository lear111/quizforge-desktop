package io.quizforge.core.port;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AssetIndexRepository {
    Optional<Asset> findById(WorkspaceId workspaceId, String assetId);

    List<Asset> list(WorkspaceId workspaceId);

    /** Atomically replace the derived index with one complete scan. */
    void synchronize(WorkspaceId workspaceId, List<Asset> assets, Instant indexedAt);
}
