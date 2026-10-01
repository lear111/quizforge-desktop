package io.quizforge.infrastructure.filesystem.markdown;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reads the registered file, rather than relying on a cached registry path or node list. */
public final class FileDocumentNodeLookup implements DocumentNodeLookup {
    private final WorkspaceFileCatalog files;
    private final RegisteredMarkdownCodec markdown = new RegisteredMarkdownCodec();
    private final LegacyMarkdownCodec standard = new LegacyMarkdownCodec();

    public FileDocumentNodeLookup(WorkspaceFileCatalog files) { this.files = files; }

    @Override public Result lookup(WorkspaceId workspace, Asset document, String nodeId) {
        String path = document.currentPath();
        String text = files.readText(workspace, path);
        if (path.toLowerCase(Locale.ROOT).endsWith(".md")) {
            var registered = markdown.parseIfRegistered(text, path);
            if (registered.isPresent()) {
                var parsed = registered.get();
                if (!document.assetId().equals(parsed.documentAssetId()))
                    throw new IllegalStateException("Document identity changed");
                return new Result(parsed.contentId(), parsed.addressableBlocks().stream()
                        .anyMatch(block -> block.nodeId().equals(nodeId)));
            }
            var parsed = standard.parseLegacy(text)
                    .orElseThrow(() -> new IllegalStateException("Document is no longer registered"));
            if (!document.assetId().equals(parsed.assetId()))
                throw new IllegalStateException("Document identity changed");
            boolean found = Pattern.compile("<!--\\s*qf:id=" + Pattern.quote(nodeId) + "\\s*-->")
                    .matcher(text).find();
            return new Result(parsed.contentId(), found);
        }
        throw new IllegalArgumentException("Unsupported document format: " + path);
    }

    @Override public AnchorResult lookupAnchor(WorkspaceId workspace, Asset document,
            String anchorName, int occurrence) {
        String path = document.currentPath();
        String text = files.readText(workspace, path);
        if (path.toLowerCase(Locale.ROOT).endsWith(".md")) {
            var registered = markdown.parseIfRegistered(text, path);
            if (registered.isPresent()) {
                var parsed = registered.get();
                if (!document.assetId().equals(parsed.documentAssetId()))
                    throw new IllegalStateException("Document identity changed");
                var match = parsed.anchors().stream().filter(anchor -> anchor.name().equals(anchorName)
                        && anchor.occurrence() == occurrence).findFirst();
                return new AnchorResult(parsed.contentId(), match.isPresent(),
                        match.isPresent() && match.get().orphan());
            }
            var legacy = standard.parseLegacy(text)
                    .orElseThrow(() -> new IllegalStateException("Document is no longer reference-enabled"));
            if (!document.assetId().equals(legacy.assetId()))
                throw new IllegalStateException("Document identity changed");
            boolean found = occurrence == 1 && Pattern.compile("<!--\\s*qf:id="
                    + Pattern.quote(anchorName) + "\\s*-->").matcher(text).find();
            return new AnchorResult(legacy.contentId(), found, false);
        }
        throw new IllegalArgumentException("Unsupported document format: " + path);
    }

    @Override public AnchorResult lookupNamedAnchor(WorkspaceId workspace, Asset document,
            String anchorName, int occurrence) {
        String path = document.currentPath();
        if (!path.toLowerCase(Locale.ROOT).endsWith(".md"))
            throw new IllegalArgumentException("Unsupported document format: " + path);
        String text = files.readText(workspace, path);
        var registered = markdown.parseIfRegistered(text, path);
        String contentId;
        java.util.List<io.quizforge.core.document.registered.NamedMarkdownAnchor> anchors;
        if (registered.isPresent()) {
            var parsed = registered.get();
            if (!document.assetId().equals(parsed.documentAssetId()))
                throw new IllegalStateException("Document identity changed");
            contentId = parsed.contentId();
            anchors = parsed.anchors();
        } else {
            var parsed = standard.parseLegacy(text)
                    .orElseThrow(() -> new IllegalStateException("Document is no longer registered"));
            if (!document.assetId().equals(parsed.assetId()))
                throw new IllegalStateException("Document identity changed");
            contentId = parsed.contentId();
            anchors = markdown.inspectAnchors(text);
        }
        var match = anchors.stream().filter(anchor -> anchor.name().equals(anchorName)
                && anchor.occurrence() == occurrence).findFirst();
        return new AnchorResult(contentId, match.isPresent(), match.isPresent() && match.get().orphan());
    }
}
