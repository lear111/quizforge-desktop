package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.asset.WorkspaceScanResult;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionSourceLinkService;
import io.quizforge.core.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QuestionSourceNavigationAdapterTest {
    private static final String A = "qfd:v2:" + "a".repeat(64);
    private static final String B = "qfd:v2:" + "b".repeat(64);
    private final WorkspaceId workspace = WorkspaceId.newId();
    private List<Asset> assets = List.of(asset("doc_a", "Java/Java集合.md", A));
    private String revision = A;
    private boolean found = true;
    private boolean orphan;
    private final AtomicReference<QuizForgeNavigationLink> target = new AtomicReference<>();
    private final AtomicReference<String> feedback = new AtomicReference<>();
    private final AssetIndexRepository index = new AssetIndexRepository() {
        @Override public Optional<Asset> findById(WorkspaceId id, String assetId) {
            return assets.stream().filter(asset -> asset.assetId().equals(assetId)).findFirst();
        }
        @Override public List<Asset> list(WorkspaceId id) { return assets; }
        @Override public void synchronize(WorkspaceId id, List<Asset> values, Instant time) { }
    };
    private final DocumentNodeLookup nodes = new DocumentNodeLookup() {
        @Override public Result lookup(WorkspaceId id, Asset document, String nodeId) {
            return new Result(revision, true);
        }
        @Override public AnchorResult lookupAnchor(WorkspaceId id, Asset document, String name, int occurrence) {
            if (name.equals("unreadable")) throw new IllegalStateException("File disappeared");
            return new AnchorResult(revision, found && name.equals("定义") && occurrence <= 2, orphan);
        }
    };
    private final QuestionBankReferenceResolver resolver = new QuestionBankReferenceResolver(
            id -> new WorkspaceScanResult(assets, List.of()), nodes);
    private final QuestionSourceNavigationAdapter adapter = new QuestionSourceNavigationAdapter(
            resolver, new QuestionSourceLinkService(index, nodes), target::set, feedback::set);

    @Test void exactSourceUsesCurrentFilenameAndPreservesDuplicateOccurrence() {
        var ref = ref("定义", 2);
        var state = adapter.inspect(workspace, List.of(ref)).getFirst();
        assertEquals("Java集合 · 定义", state.label());
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH, state.status());
        assertEquals("", state.message());
        assertTrue(state.navigable());
        adapter.open(workspace, ref);
        assertEquals(QuizForgeNavigationLink.anchor("doc_a", "定义", 2), target.get());
        assets = List.of(asset("doc_a", "Renamed/Collections.md", A));
        assertEquals("Collections · 定义", adapter.inspect(workspace, List.of(ref)).getFirst().label());
        adapter.open(workspace, ref);
        assertEquals("doc_a", target.get().assetId());
        assertEquals(A, ref.documentContentId());
    }

    @Test void differentRevisionIsNavigableWithoutAcceptingTheCurrentRevision() {
        var ref = ref("定义", 1);
        revision = B;
        assets = List.of(asset("doc_a", "Java/Java集合.md", B));
        var state = adapter.inspect(workspace, List.of(ref)).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.DIFFERENT_REVISION, state.status());
        assertEquals("来源已修改", state.message());
        assertTrue(state.navigable());
        adapter.open(workspace, ref);
        assertNotNull(target.get());
        assertEquals(A, ref.documentContentId());
    }

    @Test void missingAndOrphanAnchorsTakePriorityOverChangedRevision() {
        revision = B;
        assets = List.of(asset("doc_a", "Java/Java集合.md", B));
        found = false;
        var missing = adapter.inspect(workspace, List.of(ref("定义", 1))).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_ANCHOR, missing.status());
        assertEquals("来源位置缺失", missing.message());
        assertFalse(missing.navigable());
        found = true; orphan = true;
        var lost = adapter.inspect(workspace, List.of(ref("定义", 1))).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.ORPHAN_ANCHOR, lost.status());
        assertEquals("来源锚点无有效内容", lost.message());
        assertFalse(lost.navigable());
    }

    @Test void sourcesHaveIndependentStatusesAndLegacyNodesRemainReadOnly() {
        var legacy = new QuestionBankFile.SourceRef("doc_a", A, "node_old", "Old", "Node");
        var states = adapter.inspect(workspace, List.of(ref("定义", 1), ref("absent", 1),
                ref("定义", 2), ref("unreadable", 1), legacy));
        assertTrue(states.get(0).navigable());
        assertFalse(states.get(1).navigable());
        assertEquals(states.get(0).label(), states.get(2).label());
        assertTrue(states.get(2).navigable());
        assertEquals(QuestionBankReferenceResolver.Status.UNAVAILABLE_DOCUMENT, states.get(3).status());
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH, states.get(4).status());
        assertFalse(states.get(4).navigable());
        assertEquals("旧版节点引用", states.get(4).message());
    }

    @Test void missingIdentityDoesNotRebindToAnExactContentCopy() {
        assets = List.of(asset("doc_copy", "Copy.md", A));
        var state = adapter.inspect(workspace, List.of(ref("定义", 1))).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_DOCUMENT, state.status());
        assertFalse(state.navigable());
        adapter.open(workspace, ref("定义", 1));
        assertNull(target.get());
        assertEquals("来源文档缺失", feedback.get());
    }

    @Test void clickRechecksAPreviouslyAvailableTarget() {
        var ref = ref("定义", 1);
        assertTrue(adapter.inspect(workspace, List.of(ref)).getFirst().navigable());
        found = false;
        adapter.open(workspace, ref);
        assertNull(target.get());
        assertEquals("来源位置缺失", feedback.get());
        assertEquals(A, ref.documentContentId());
    }

    @Test void unexpectedNavigationFailureIsReportedWithoutChangingTheReference() {
        var ref = ref("定义", 1);
        var failing = new QuestionSourceNavigationAdapter(resolver, new QuestionSourceLinkService(index, nodes),
                link -> { throw new IllegalStateException("File unavailable during navigation"); }, feedback::set);
        assertDoesNotThrow(() -> failing.open(workspace, ref));
        assertEquals("来源暂时无法打开。", feedback.get());
        assertEquals(A, ref.documentContentId());
    }

    private QuestionBankFile.SourceRef ref(String name, int occurrence) {
        return QuestionBankFile.SourceRef.anchor("doc_a", A, name, occurrence, "Snapshot title", "Snapshot section");
    }
    private static Asset asset(String id, String path, String revision) {
        return new Asset(id, AssetType.STANDARD_DOCUMENT, path, "Old title", revision, "1.0");
    }
}
