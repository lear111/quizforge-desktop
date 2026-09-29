package io.quizforge.core.question;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.FormalDocumentReader;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.port.QuestionBankFileStorage;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Edits a portable bank in place; new references must resolve to current valid Markdown documents. */
public final class QuestionBankFileEditService {
    private final WorkspaceService workspaces;
    private final QuestionBankFileStorage files;
    private final QuestionBankFileCodec codec;
    private final WorkspaceAssetScanner scanner;
    private final FormalDocumentReader documents;
    private final DocumentNodeLookup nodes;

    public QuestionBankFileEditService(WorkspaceService workspaces, QuestionBankFileStorage files,
            QuestionBankFileCodec codec, WorkspaceAssetScanner scanner, FormalDocumentReader documents) {
        this(workspaces, files, codec, scanner, documents, null);
    }

    public QuestionBankFileEditService(WorkspaceService workspaces, QuestionBankFileStorage files,
            QuestionBankFileCodec codec, WorkspaceAssetScanner scanner, FormalDocumentReader documents,
            DocumentNodeLookup nodes) {
        this.workspaces = workspaces;
        this.files = files;
        this.codec = codec;
        this.scanner = scanner;
        this.documents = documents;
        this.nodes = nodes;
    }

    public List<Asset> availableSources(WorkspaceId workspace) {
        workspaces.getWorkspace(workspace);
        return scanner.scan(workspace).stream().filter(asset ->
                asset.assetType() == AssetType.STANDARD_DOCUMENT
                        && asset.currentPath().toLowerCase(java.util.Locale.ROOT).endsWith(".md")
                        && asset.contentId() != null && asset.contentId().startsWith("qfd:v1:"))
                .toList();
    }

    public SourceDocumentSnapshot source(WorkspaceId workspace, String assetId) {
        Asset asset = availableSources(workspace).stream().filter(item -> item.assetId().equals(assetId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Source Markdown document is unavailable"));
        SourceDocumentSnapshot snapshot = documents.read(workspace, asset.currentPath());
        if (!snapshot.assetId().equals(asset.assetId()) || !snapshot.contentId().equals(asset.contentId()))
            throw new IllegalStateException("Source Markdown document changed during lookup");
        return snapshot;
    }

    public Asset save(WorkspaceId workspace, String path, String expectedContentId, QuestionBank edited) {
        return save(workspace, path, expectedContentId, null, edited);
    }

    public Asset save(WorkspaceId workspace, String path, String expectedContentId,
            String expectedSourceText, QuestionBank edited) {
        workspaces.getWorkspace(workspace);
        if (path == null || !path.toLowerCase(java.util.Locale.ROOT).endsWith(".qbank"))
            throw new IllegalArgumentException("Only .qbank files can be edited here");
        QuestionBank saved = edited;
        codec.validate(saved);
        String content = codec.write(saved);
        String revision = codec.contentId(saved);
        String currentText = files.read(workspace, path);
        QuestionBank current = expectedContentId == null ? codec.parseEmptyDraft(currentText)
                : codec.parse(currentText);
        if (!current.assetId().equals(edited.assetId())) throw new IllegalArgumentException("QuestionBank assetId cannot change");
        if (expectedContentId == null ? expectedSourceText == null || !currentText.equals(expectedSourceText)
                : !codec.contentId(current).equals(expectedContentId))
            throw new IllegalStateException("QuestionBank changed externally. Please reload before saving.");
        validateNewRefs(workspace, current, edited);
        try (QuestionBankFileStorage.StagedFile staged = files.stageReplace(workspace, path, content)) {
            staged.publish();
            try {
                Asset registered = scanner.scan(workspace).stream()
                        .filter(asset -> asset.assetType() == AssetType.QUESTION_BANK
                                && asset.assetId().equals(edited.assetId()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Saved QuestionBank was not indexed"));
                if (!path.equals(registered.currentPath()) || !revision.equals(registered.contentId()))
                    throw new IllegalStateException("Asset Registry does not match the saved QuestionBank");
                QuestionBank reread = codec.parse(files.read(workspace, path));
                if (!revision.equals(codec.contentId(reread)))
                    throw new IllegalStateException("Saved QuestionBank revision changed unexpectedly");
                staged.complete();
                return registered;
            } catch (RuntimeException failure) {
                try { staged.rollback(); scanner.scan(workspace); }
                catch (RuntimeException recovery) { failure.addSuppressed(recovery); }
                throw failure;
            }
        }
    }

    private void validateNewRefs(WorkspaceId workspace, QuestionBank current, QuestionBank edited) {
        Set<SourceRef> existing = new HashSet<>();
        current.questions().forEach(question -> existing.addAll(question.sourceRefs()));
        Map<String, Asset> registered = new HashMap<>();
        scanner.scan(workspace).stream().filter(asset -> asset.assetType() == AssetType.STANDARD_DOCUMENT)
                .forEach(asset -> registered.put(asset.assetId(), asset));
        for (var question : edited.questions()) for (var ref : question.sourceRefs()) {
            if (existing.contains(ref)) continue;
            Asset asset = registered.get(ref.documentAssetId());
            if (nodes == null || asset == null || !asset.currentPath().toLowerCase(java.util.Locale.ROOT).endsWith(".md"))
                throw new IllegalArgumentException("Source Markdown is unavailable");
            var found = nodes.lookupNamedAnchor(workspace, asset, ref.anchorName(), ref.occurrence());
            if (!found.containsAnchor() || found.orphan()
                    || !ref.documentContentId().equals(found.contentId()))
                throw new IllegalArgumentException("Source Anchor is not in the current Markdown revision");
        }
    }
}
