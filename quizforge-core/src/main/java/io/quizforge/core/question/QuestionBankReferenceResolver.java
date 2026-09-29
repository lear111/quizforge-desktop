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
        MISSING_DOCUMENT, MISSING_NODE, MISSING_ANCHOR, ORPHAN_ANCHOR, UNAVAILABLE_DOCUMENT, MISSING }
    public record Resolution(QuestionSourceDocument source, Status status,
            List<Asset> candidates) {
        public Resolution { candidates = List.copyOf(candidates); }
        public boolean ambiguous() { return candidates.size() > 1; }
    }
    public record NodeResolution(SourceRef sourceRef, Status status,
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
    public List<NodeResolution> resolveRefs(WorkspaceId workspaceId, QuestionBank bank) {
        if (nodes == null) throw new IllegalStateException("Document node lookup is not configured");
        List<Asset> documents = scanner.scan(workspaceId).stream()
                .filter(asset -> asset.assetType() == AssetType.STANDARD_DOCUMENT).toList();
        return bank.questions().stream().flatMap(question -> question.sourceRefs().stream())
                .map(ref -> resolveRef(workspaceId, documents, ref)).toList();
    }

    /** Current-view resolution uses the recorded identity only; it never rebinds by content. */
    public List<NodeResolution> resolveCurrentRefs(WorkspaceId workspaceId,
            List<SourceRef> refs) {
        if (nodes == null) throw new IllegalStateException("Document node lookup is not configured");
        List<Asset> documents = scanner.scan(workspaceId).stream()
                .filter(asset -> asset.assetType() == AssetType.STANDARD_DOCUMENT).toList();
        return refs.stream().map(ref -> {
            List<Asset> byId = documents.stream()
                    .filter(asset -> asset.assetId().equals(ref.documentAssetId())).toList();
            if (byId.isEmpty()) return new NodeResolution(ref, Status.MISSING_DOCUMENT, List.of());
            try {
                return new NodeResolution(ref, resolveDocumentRef(workspaceId, byId.getFirst(), ref, true), byId);
            } catch (RuntimeException error) {
                return new NodeResolution(ref, Status.UNAVAILABLE_DOCUMENT, byId);
            }
        }).toList();
    }

    private Status resolveDocumentRef(WorkspaceId workspaceId, Asset document,
            SourceRef ref, boolean namedOnly) {
        if (ref.address().kind() == QuestionSourceAddress.Kind.ANCHOR) {
            DocumentNodeLookup.AnchorResult found = namedOnly
                    ? nodes.lookupNamedAnchor(workspaceId, document, ref.anchorName(), ref.occurrence())
                    : nodes.lookupAnchor(workspaceId, document, ref.anchorName(), ref.occurrence());
            if (!found.containsAnchor()) return Status.MISSING_ANCHOR;
            if (found.orphan()) return Status.ORPHAN_ANCHOR;
            return ref.documentContentId().equals(found.contentId())
                    ? Status.EXACT_MATCH : Status.DIFFERENT_REVISION;
        }
        // Preserve the existing revision-first behavior of legacy node references.
        if (!ref.documentContentId().equals(document.contentId())) return Status.DIFFERENT_REVISION;
        DocumentNodeLookup.Result found = nodes.lookup(workspaceId, document, ref.nodeId());
        if (!ref.documentContentId().equals(found.contentId())) return Status.DIFFERENT_REVISION;
        return found.containsNode() ? Status.EXACT_MATCH : Status.MISSING_NODE;
    }

    private NodeResolution resolveRef(WorkspaceId workspaceId, List<Asset> documents,
            SourceRef ref) {
        List<Asset> byId = documents.stream()
                .filter(asset -> asset.assetId().equals(ref.documentAssetId())).toList();
        if (!byId.isEmpty()) {
            return new NodeResolution(ref, resolveDocumentRef(workspaceId, byId.getFirst(), ref, false), byId);
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

    public List<Resolution> resolve(WorkspaceId workspaceId, QuestionBank bank) {
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
