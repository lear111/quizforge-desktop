package io.quizforge.desktop.ui.question.source;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.asset.WorkspaceScanResult;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.DocumentNodeLookup;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeQuestionSnapshotMapper;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import io.quizforge.core.question.source.QuestionSourceAddress;
import io.quizforge.core.question.source.QuestionSourceLinkService;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistorySourceNavigationAdapterTest {
    private static final String A = "qfd:v2:" + "a".repeat(64);
    private static final String B = "qfd:v2:" + "b".repeat(64);
    private final WorkspaceId workspace = WorkspaceId.newId();
    private List<Asset> assets = List.of(asset("doc_history", "Java/Collections.md", A));
    private String revision = A;
    private boolean missing;
    private boolean orphan;
    private final AtomicReference<QuizForgeNavigationLink> navigation = new AtomicReference<>();
    private final AtomicReference<String> feedback = new AtomicReference<>();
    private final DocumentNodeLookup nodes = new DocumentNodeLookup() {
        @Override public Result lookup(WorkspaceId id, Asset document, String nodeId) {
            return new Result(revision, !missing);
        }
        @Override public AnchorResult lookupAnchor(WorkspaceId id, Asset document, String name, int occurrence) {
            return new AnchorResult(revision, !missing && name.equals("定义") && occurrence <= 2, orphan);
        }
    };
    private final AssetIndexRepository index = new AssetIndexRepository() {
        @Override public Optional<Asset> findById(WorkspaceId id, String assetId) {
            return list(id).stream().filter(asset -> asset.assetId().equals(assetId)).findFirst();
        }
        @Override public List<Asset> list(WorkspaceId id) { return workspace.equals(id) ? assets : List.of(); }
        @Override public void synchronize(WorkspaceId id, List<Asset> values, Instant time) {
            fail("History adapter must not write the registry itself");
        }
    };
    private final HistorySourceNavigationAdapter adapter = new HistorySourceNavigationAdapter(
            new QuestionSourceNavigationAdapter(new QuestionBankReferenceResolver(
                    id -> new WorkspaceScanResult(index.list(id), List.of()), nodes),
                    new QuestionSourceLinkService(index, nodes), navigation::set, feedback::set));

    @Test void exactSnapshotHasReadableCurrentNameAndNavigates() {
        var state = adapter.inspect(workspace, snapshot(ref(1))).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH, state.status());
        assertEquals("Collections · 定义", state.label());
        assertTrue(state.navigable());
        adapter.open(workspace, state.ref());
        assertEquals(QuizForgeNavigationLink.anchor("doc_history", "定义", 1), navigation.get());
    }

    @Test void changedRevisionNavigatesWithoutUpdatingFrozenSnapshot() {
        var snapshot = snapshot(ref(1));
        var before = snapshot.value();
        revision = B;
        assets = List.of(asset("doc_history", "Java/Collections.md", B));
        var state = adapter.inspect(workspace, snapshot).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.DIFFERENT_REVISION, state.status());
        assertEquals("来源已修改", state.message());
        assertTrue(state.navigable());
        adapter.open(workspace, state.ref());
        assertNotNull(navigation.get());
        assertEquals(A, state.ref().documentContentId());
        assertEquals(before, snapshot.value());
    }

    @Test void missingDocumentFailsClosedAndNeverBindsAnIdenticalCopy() {
        assets = List.of(asset("doc_copy", "Copy.md", A));
        var state = adapter.inspect(workspace, snapshot(ref(1))).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_DOCUMENT, state.status());
        assertEquals("来源文档缺失", state.message());
        assertFalse(state.navigable());
        adapter.open(workspace, state.ref());
        assertNull(navigation.get());
    }

    @Test void missingAnchorIsNotNavigableEvenWhenRevisionChanged() {
        revision = B; missing = true;
        var state = adapter.inspect(workspace, snapshot(ref(2))).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_ANCHOR, state.status());
        assertEquals("来源位置缺失", state.message());
        assertFalse(state.navigable());
        adapter.open(workspace, state.ref());
        assertNull(navigation.get());
    }

    @Test void orphanAnchorIsNotNavigable() {
        orphan = true;
        var state = adapter.inspect(workspace, snapshot(ref(1))).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.ORPHAN_ANCHOR, state.status());
        assertEquals("来源锚点无有效内容", state.message());
        assertFalse(state.navigable());
        adapter.open(workspace, state.ref());
        assertNull(navigation.get());
    }

    @Test void duplicateLabelsStayPlainButNavigateToDifferentOccurrences() {
        var states = adapter.inspect(workspace, snapshot(ref(1), ref(2)));
        assertEquals(states.get(0).label(), states.get(1).label());
        assertFalse(states.get(1).label().contains("#2"));
        adapter.open(workspace, states.get(1).ref());
        assertEquals(QuizForgeNavigationLink.anchor("doc_history", "定义", 2), navigation.get());
    }

    @Test void multipleSourcesResolveIndependentlyFromTheArchivedPayload() {
        var absent = SourceRef.anchor("doc_gone", A, "旧来源", 1, "Gone", "Old");
        var states = adapter.inspect(workspace, snapshot(ref(1), absent, ref(2)));
        assertEquals(List.of(true, false, true), states.stream().map(s -> s.navigable()).toList());
        assertEquals(absent, states.get(1).ref());
    }

    @Test void renamedAndMovedDocumentUsesCurrentRegistryName() {
        var snapshot = snapshot(ref(2));
        assets = List.of(asset("doc_history", "Other/Java集合框架.md", A));
        var state = adapter.inspect(workspace, snapshot).getFirst();
        assertEquals("Java集合框架 · 定义", state.label());
        adapter.open(workspace, state.ref());
        assertEquals("doc_history", navigation.get().assetId());
        assertEquals(A, state.ref().documentContentId());
    }

    @Test void legacySectionAndNodeAddressesArePreservedWithoutInventingAnchors() {
        var section = new SourceRef("doc_history", A,
                QuestionSourceAddress.section("section_old"), "Old", "Section");
        var node = new SourceRef("doc_history", A, "node_old", "Old", "Node");
        // This is a frozen historical payload, independent of the current QBank writer.
        var archived = new PracticePayload(List.of(
                Map.of("documentAssetId", "doc_history", "documentContentId", A,
                        "sectionId", "section_old", "documentTitle", "Old", "sectionTitle", "Section"),
                Map.of("documentAssetId", "doc_history", "documentContentId", A,
                        "nodeId", "node_old", "documentTitle", "Old", "sectionTitle", "Node")));
        var states = adapter.inspect(workspace, archived);
        assertEquals(section, states.get(0).ref());
        assertEquals(node, states.get(1).ref());
        assertTrue(states.stream().allMatch(s -> s.status() == QuestionBankReferenceResolver.Status.EXACT_MATCH));
        assertTrue(states.stream().noneMatch(s -> s.navigable()));
        missing = true;
        assertTrue(adapter.inspect(workspace, archived).stream()
                .allMatch(s -> s.status() == QuestionBankReferenceResolver.Status.MISSING_NODE));
    }

    @Test void wrongWorkspaceFailsClosed() {
        var other = WorkspaceId.newId();
        var state = adapter.inspect(other, snapshot(ref(1))).getFirst();
        assertFalse(state.navigable());
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_DOCUMENT, state.status());
        adapter.open(other, state.ref());
        assertNull(navigation.get());
    }

    @Test void clickRechecksAvailabilityInsteadOfTrustingDisplayedState() {
        var state = adapter.inspect(workspace, snapshot(ref(1))).getFirst();
        assertTrue(state.navigable());
        assets = List.of();
        assertDoesNotThrow(() -> adapter.open(workspace, state.ref()));
        assertNull(navigation.get());
        assertEquals("来源文档缺失", feedback.get());
    }

    @Test void fractionalOccurrenceAndAmbiguousAddressAreRejectedWithoutNavigation() {
        var fields = Map.of("documentAssetId", "doc_history", "documentContentId", A,
                "anchorName", "定义", "occurrence", 1.5);
        assertThrows(ArithmeticException.class, () -> adapter.inspect(workspace, new PracticePayload(List.of(fields))));
        assertThrows(IllegalArgumentException.class, () -> adapter.inspect(workspace, new PracticePayload(List.of(
                Map.of("documentAssetId", "doc_history", "documentContentId", A,
                        "anchorName", "定义", "occurrence", 1, "nodeId", "fake")))));
        assertNull(navigation.get());
    }

    private static PracticePayload snapshot(SourceRef... refs) {
        return new PracticeQuestionSnapshotMapper().map(Question.choice("archived_question", "SINGLE_CHOICE", new TextContent("Historical question"), new TextContent("Analysis"), List.of(refs), new ChoicePayload(List.of(new ChoiceOption("a", new TextContent("A")),
                        new ChoiceOption("b", new TextContent("B")))), new ChoiceAnswerSpec(List.of("a")))).sourceRefs();
    }
    private static SourceRef ref(int occurrence) {
        return SourceRef.anchor("doc_history", A, "定义", occurrence, "Snapshot title", "Old");
    }
    private static Asset asset(String id, String path, String revision) {
        return new Asset(id, AssetType.REGISTERED_MARKDOWN, path, "Current title", revision, "1");
    }
}
