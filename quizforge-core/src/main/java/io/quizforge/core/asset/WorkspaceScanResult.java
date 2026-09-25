package io.quizforge.core.asset;

import java.util.List;

public record WorkspaceScanResult(List<Asset> assets, List<WorkspaceScanIssue> issues) {
    public WorkspaceScanResult {
        assets = List.copyOf(assets);
        issues = List.copyOf(issues);
    }
}
