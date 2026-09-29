package io.quizforge.core.question;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.Locale;

/** Converts an explicit Source input into a revision-pinned QBank anchor reference. */
public final class QuestionSourceLinkService {
    private final AssetIndexRepository index;
    private final DocumentNodeLookup nodes;
    private final MarkdownNavigationLinkCodec links = new MarkdownNavigationLinkCodec();

    public QuestionSourceLinkService(AssetIndexRepository index, DocumentNodeLookup nodes) {
        this.index = index;
        this.nodes = nodes;
    }

    public SourceRef resolve(WorkspaceId workspace, String input) {
        QuizForgeNavigationLink link = links.parse(input).link();
        if (!(link.target() instanceof QuizForgeNavigationLink.AnchorTarget anchor))
            throw new IllegalArgumentException("题目来源目前需要使用 Source Anchor。");
        Asset asset = index.findById(workspace, link.assetId())
                .orElseThrow(() -> new IllegalArgumentException("Source document is missing"));
        if (asset.assetType() != AssetType.STANDARD_DOCUMENT
                || !asset.currentPath().toLowerCase(Locale.ROOT).endsWith(".md"))
            throw new IllegalArgumentException("Source must be registered Markdown");
        DocumentNodeLookup.AnchorResult found;
        try { found = nodes.lookupNamedAnchor(workspace, asset, anchor.anchorName(), anchor.occurrence()); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Source Markdown cannot be read", error); }
        if (!found.containsAnchor() || found.orphan())
            throw new IllegalArgumentException(found.orphan() ? "Source Anchor has no content"
                    : "Source Anchor is missing");
        if (found.contentId() == null || !found.contentId().matches("qfd:v[12]:[0-9a-f]{64}"))
            throw new IllegalArgumentException("Source document revision is unavailable");
        return SourceRef.anchor(asset.assetId(), found.contentId(),
                anchor.anchorName(), anchor.occurrence(), filename(asset.currentPath()), anchor.anchorName());
    }

    public String displayName(WorkspaceId workspace, SourceRef ref) {
        String name = index.findById(workspace, ref.documentAssetId())
                .map(asset -> filename(asset.currentPath())).orElse("缺失文档");
        String target = ref.anchorName() == null ? ref.sectionTitle() : ref.anchorName();
        return name + " · " + target;
    }

    private static String filename(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.toLowerCase(Locale.ROOT).endsWith(".md")
                ? name.substring(0, name.length() - 3) : name;
    }
}
