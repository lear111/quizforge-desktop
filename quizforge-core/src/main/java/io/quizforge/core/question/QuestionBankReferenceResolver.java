package io.quizforge.core.question;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;

/** Runtime resolution never mutates the portable QuestionBank's recorded source IDs. */
public final class QuestionBankReferenceResolver {
    public enum Status { EXACT_MATCH, DIFFERENT_REVISION, EXACT_CONTENT_MATCH, MISSING }
    public record Resolution(QuestionBankFile.SourceDocument source, Status status,
            List<Asset> candidates) {
        public Resolution { candidates = List.copyOf(candidates); }
        public boolean ambiguous() { return candidates.size() > 1; }
    }

    private final WorkspaceAssetScanner scanner;

    public QuestionBankReferenceResolver(WorkspaceAssetScanner scanner) { this.scanner = scanner; }

    public List<Resolution> resolve(WorkspaceId workspaceId, QuestionBankFile bank) {
        List<Asset> documents = scanner.scan(workspaceId).stream()
                .filter(asset -> asset.assetType() == AssetType.STANDARD_DOCUMENT).toList();
        return bank.sourceDocuments().stream().map(source -> {
            List<Asset> byId = documents.stream()
                    .filter(asset -> asset.assetId().equals(source.assetId())).toList();
            if (!byId.isEmpty()) return new Resolution(source,
                    source.contentId().equals(byId.getFirst().contentId())
                            ? Status.EXACT_MATCH : Status.DIFFERENT_REVISION, byId);
            List<Asset> byContent = documents.stream()
                    .filter(asset -> source.contentId().equals(asset.contentId())).toList();
            return new Resolution(source, byContent.isEmpty() ? Status.MISSING : Status.EXACT_CONTENT_MATCH,
                    byContent);
        }).toList();
    }
}
