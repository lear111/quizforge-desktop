package io.quizforge.core.question.service;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.port.QuestionBankFileStorage;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.core.workspace.service.WorkspaceService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Edits a portable bank in place; new references must resolve to current valid Markdown documents. */
public final class QuestionBankFileEditService {
    private final WorkspaceService workspaces;
    private final QuestionBankFileStorage files;
    private final QuestionBankFileCodec codec;
    private final WorkspaceAssetScanner scanner;
    private final DocumentNodeLookup nodes;

    public QuestionBankFileEditService(WorkspaceService workspaces, QuestionBankFileStorage files,
            QuestionBankFileCodec codec, WorkspaceAssetScanner scanner, DocumentNodeLookup nodes) {
        this.workspaces=workspaces;this.files=files;this.codec=codec;this.scanner=scanner;this.nodes=nodes;
    }

    public Asset save(WorkspaceId workspace, String path, String expectedContentId, QuestionBank edited) {
        return save(workspace, path, expectedContentId, null, edited);
    }

    public Asset save(WorkspaceId workspace, String path, String expectedContentId,
            String expectedDraftContentId, QuestionBank edited) {
        return save(workspace,path,expectedContentId,expectedDraftContentId,edited,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    public Asset save(WorkspaceId workspace, String path, String expectedContentId,
            String expectedDraftContentId, QuestionBank edited, io.quizforge.core.port.QuestionResourceInput resources) {
        workspaces.getWorkspace(workspace);
        if (path == null || !path.toLowerCase(java.util.Locale.ROOT).endsWith(".qbank"))
            throw new IllegalArgumentException("Only .qbank files can be edited here");
        QuestionBank saved = edited;
        codec.validate(saved);
        String revision = codec.contentId(saved);
        QuestionBank current = files.read(workspace, path);
        if (expectedContentId != null) codec.validate(current);
        if (!current.assetId().equals(edited.assetId())) throw new IllegalArgumentException("QuestionBank assetId cannot change");
        if (expectedContentId == null ? expectedDraftContentId == null || !codec.contentId(current).equals(expectedDraftContentId)
                : !codec.contentId(current).equals(expectedContentId))
            throw new IllegalStateException("QuestionBank changed externally. Please reload before saving.");
        validateNewRefs(workspace, current, edited);
        try (QuestionBankFileStorage.StagedFile staged = files.stageReplace(workspace, path, saved, resources, current)) {
            staged.publish();
            try {
                Asset registered = scanner.scan(workspace).stream()
                        .filter(asset -> asset.assetType() == AssetType.QUESTION_BANK
                                && asset.assetId().equals(edited.assetId()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Saved QuestionBank was not indexed"));
                if (!path.equals(registered.currentPath()) || !revision.equals(registered.contentId()))
                    throw new IllegalStateException("Asset Registry does not match the saved QuestionBank");
                QuestionBank reread = files.read(workspace, path);
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
        scanner.scan(workspace).stream().filter(asset -> asset.assetType() == AssetType.REGISTERED_MARKDOWN)
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
