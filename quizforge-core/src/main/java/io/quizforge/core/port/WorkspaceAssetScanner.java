package io.quizforge.core.port;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.WorkspaceScanResult;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;

public interface WorkspaceAssetScanner {
    WorkspaceScanResult scanWithReport(WorkspaceId workspaceId);

    default List<Asset> scan(WorkspaceId workspaceId) {
        return scanWithReport(workspaceId).assets();
    }
}
