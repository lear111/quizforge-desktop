package io.quizforge.core.question;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;

/** Runtime resolution never mutates the portable QuestionBank's recorded source IDs. */
public final class QuestionBankReferenceResolver {
    public enum Status { EXACT_MATCH, DIFFERENT_REVISION, EXACT_CONTENT_MATCH,
        MISSING_DOCUMENT, MISSING_NODE, MISSING_ANCHOR, ORPHAN_ANCHOR, MISSING }
    public record Resolution(QuestionBankFile.SourceDocument source, Status status,
            List<Asset> candidates) {
        public Resolution { candidates = List.copyOf(candidates); }
        public boolean ambiguous() { return candidates.size() > 1; }
    }
    public record NodeResolution(QuestionBankFile.SourceRef sourceRef, Status status,
            List<Asset> candidates) {
        public NodeResolution { candidates = List.copyOf(candidates); }
        public boolean ambiguous() { return candidates.size() > 1; }
    }

    private final WorkspaceAssetScanner scanner;
    private final DocumentNodeLookup nodes;

    public QuestionBankReferenceResolver(WorkspaceAssetScanner scanner) { this(scanner, null); }
    public QuestionBankReferenceResolver(WorkspaceAssetScanner scanner, DocumentNodeLookup nodes) {
        this.scanner = scanner;
        this.nodes = nodes;
    }

    /** Resolves every question reference against the current document file and its node IDs. */
    public List<NodeResolution> resolveRefs(WorkspaceId workspaceId, QuestionBankFile bank) {
        if (nodes == null) throw new IllegalStateException("Document node lookup is not configured");
        List<Asset> documents = scanner.scan(workspaceId).stream()
                .filter(asset -> asset.assetType() == AssetType.STANDARD_DOCUMENT).toList();
        return bank.questions().stream().flatMap(question -> question.sourceRefs().stream())
                .map(ref -> resolveRef(workspaceId, documents, ref)).toList();
    }

    private NodeResolution resolveRef(WorkspaceId workspaceId, List<Asset> documents,
            QuestionBankFile.SourceRef ref) {
        List<Asset> byId = documents.stream()
                .filter(asset -> asset.assetId().equals(ref.documentAssetId())).toList();
        if (!byId.isEmpty()) {
            Asset document = byId.getFirst();
            if (!ref.documentContentId().equals(document.contentId()))
                return new NodeResolution(ref, Status.DIFFERENT_REVISION, byId);
            if (ref.address().kind() == QuestionSourceAddress.Kind.ANCHOR) {
                DocumentNodeLookup.AnchorResult found = nodes.lookupAnchor(workspaceId, document,
                        ref.anchorName(), ref.occurrence());
                if (!ref.documentContentId().equals(found.contentId()))
                    return new NodeResolution(ref, Status.DIFFERENT_REVISION, byId);
                return new NodeResolution(ref, !found.containsAnchor() ? Status.MISSING_ANCHOR
                        : found.orphan() ? Status.ORPHAN_ANCHOR : Status.EXACT_MATCH, byId);
            }
            DocumentNodeLookup.Result found = nodes.lookup(workspaceId, document, ref.nodeId());
            if (!ref.documentContentId().equals(found.contentId()))
                return new NodeResolution(ref, Status.DIFFERENT_REVISION, byId);
            return new NodeResolution(ref, found.containsNode() ? Status.EXACT_MATCH : Status.MISSING_NODE, byId);
        }
        List<Asset> byContent = documents.stream()
                .filter(asset -> ref.documentContentId().equals(asset.contentId())).toList();
        if (byContent.isEmpty()) return new NodeResolution(ref, Status.MISSING_DOCUMENT, List.of());
        List<Asset> matching = byContent.stream().filter(asset -> {
            if (ref.address().kind() == QuestionSourceAddress.Kind.ANCHOR) {
                DocumentNodeLookup.AnchorResult found = nodes.lookupAnchor(workspaceId, asset,
                        ref.anchorName(), ref.occurrence());
                return ref.documentContentId().equals(found.contentId())
                        && found.containsAnchor() && !found.orphan();
            }
            DocumentNodeLookup.Result found = nodes.lookup(workspaceId, asset, ref.nodeId());
            return ref.documentContentId().equals(found.contentId()) && found.containsNode();
        }).toList();
        return new NodeResolution(ref, matching.isEmpty() ? ref.address().kind()
                == QuestionSourceAddress.Kind.ANCHOR ? Status.MISSING_ANCHOR : Status.MISSING_NODE
                : Status.EXACT_CONTENT_MATCH,
                matching.isEmpty() ? byContent : matching);
    }

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
