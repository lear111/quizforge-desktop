package io.quizforge.core.question;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QuestionSourceLinkServiceTest {
    private static final String REVISION = "qfd:v2:" + "a".repeat(64);
    private final WorkspaceId workspace = WorkspaceId.newId();
    private Asset asset = new Asset("doc_a", AssetType.STANDARD_DOCUMENT,
            "Java/Java集合.md", "Java", REVISION, "1.0");
    private boolean found = true;
    private boolean orphan;
    private String actualRevision = REVISION;
    private final AtomicInteger lookups = new AtomicInteger();
    private final MarkdownNavigationLinkCodec links = new MarkdownNavigationLinkCodec();
    private final AssetIndexRepository index = new AssetIndexRepository() {
        @Override public Optional<Asset> findById(WorkspaceId id, String assetId) {
            return asset == null || !asset.assetId().equals(assetId) ? Optional.empty() : Optional.of(asset);
        }
        @Override public List<Asset> list(WorkspaceId id) { return asset == null ? List.of() : List.of(asset); }
        @Override public void synchronize(WorkspaceId id, List<Asset> assets, Instant time) { }
    };
    private final DocumentNodeLookup nodes = new DocumentNodeLookup() {
        @Override public Result lookup(WorkspaceId id, Asset document, String nodeId) {
            throw new AssertionError("Node lookup must not be used");
        }
        @Override public AnchorResult lookupAnchor(WorkspaceId id, Asset document,
                String anchorName, int occurrence) {
            lookups.incrementAndGet();
            return new AnchorResult(actualRevision, found && anchorName.equals("定义")
                    && occurrence <= 2, orphan);
        }
    };
    private final QuestionSourceLinkService service = new QuestionSourceLinkService(index, nodes);

    private String link(int occurrence) {
        return links.format("Java/Java集合.md", QuizForgeNavigationLink.anchor("doc_a", "定义", occurrence));
    }

    @Test void exactSecondAnchorUsesCurrentRevisionAndIgnoresEditedAlias() {
        String text = link(2).replace("Java集合 · 定义", "这里讲定义");
        var ref = service.resolve(workspace, text);
        assertEquals("doc_a", ref.documentAssetId());
        assertEquals(REVISION, ref.documentContentId());
        assertEquals("定义", ref.anchorName());
        assertEquals(2, ref.occurrence());
        assertEquals("Java集合 · 定义", service.displayName(workspace, ref));
        assertEquals(ref, service.resolve(workspace,
                "quizforge://asset/doc_a/anchor/%E5%AE%9A%E4%B9%89?occurrence=2"));
        assertEquals(2, lookups.get());
    }

    @Test void currentRevisionComesFromFileLookupAndDisplayFollowsRename() {
        actualRevision = "qfd:v2:" + "b".repeat(64);
        var ref = service.resolve(workspace, link(1));
        assertEquals(actualRevision, ref.documentContentId());
        asset = new Asset("doc_a", AssetType.STANDARD_DOCUMENT,
                "Renamed/Java Collections.md", "Old title", REVISION, "1.0");
        assertEquals("Java Collections · 定义", service.displayName(workspace, ref));
    }

    @Test void nonAnchorAndMissingOrOrphanTargetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.resolve(workspace,
                links.format("Java/Java集合.md", QuizForgeNavigationLink.asset("doc_a"))));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(workspace,
                links.format("Java/Java集合.md", QuizForgeNavigationLink.heading("doc_a", "H", 1))));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(workspace,
                links.format("Other.md", QuizForgeNavigationLink.anchor("doc_missing", "定义", 1))));
        found = false;
        assertThrows(IllegalArgumentException.class, () -> service.resolve(workspace, link(1)));
        found = true;
        assertThrows(IllegalArgumentException.class, () -> service.resolve(workspace, link(3)));
        orphan = true;
        assertThrows(IllegalArgumentException.class, () -> service.resolve(workspace, link(1)));
    }
}
