package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.document.qdoc.DocumentElement;
import io.quizforge.core.document.qdoc.DocumentNode;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reads the registered file, rather than relying on a cached registry path or node list. */
public final class FileDocumentNodeLookup implements DocumentNodeLookup {
    private final WorkspaceFileCatalog files;
    private final RegisteredMarkdownCodec markdown = new RegisteredMarkdownCodec();
    private final StandardKnowledgeDocumentV1 standard = new StandardKnowledgeDocumentV1();
    private final QDocV1Codec qdocs = new QDocV1Codec();

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
            var parsed = standard.parseIfStandard(text)
                    .orElseThrow(() -> new IllegalStateException("Document is no longer registered"));
            if (!document.assetId().equals(parsed.assetId()))
                throw new IllegalStateException("Document identity changed");
            boolean found = Pattern.compile("<!--\\s*qf:id=" + Pattern.quote(nodeId) + "\\s*-->")
                    .matcher(text).find();
            return new Result(parsed.contentId(), found);
        }
        if (path.toLowerCase(Locale.ROOT).endsWith(".qdoc")) {
            var parsed = qdocs.parse(text);
            if (!document.assetId().equals(parsed.id()))
                throw new IllegalStateException("Document identity changed");
            return new Result(qdocs.contentId(parsed), parsed.content().stream()
                    .anyMatch(node -> contains(node, nodeId)));
        }
        throw new IllegalArgumentException("Unsupported document format: " + path);
    }

    private boolean contains(DocumentNode node, String id) {
        if (node.id().equals(id)) return true;
        for (DocumentElement child : node.children()) {
            if (child instanceof DocumentNode nested && contains(nested, id)) return true;
        }
        return false;
    }
}
