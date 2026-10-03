package io.quizforge.desktop.poc.draftcanvas.contract;

import io.quizforge.core.practice.draft.DraftCanvasDocument;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure future-lifecycle specification tests. No Practice service, Attempt mutation or database. */
class DraftLifecycleContractTest {
    @Test void successfulSubmitFreezesImmutableGeometryAndRemovesWorkingDraft() {
        var document = inkDocument("initial-stroke");
        var contract = new DraftLifecycleContract(document);
        var frozen = contract.onSubmitSuccess("external-initial-attempt");
        assertEquals(document, frozen.document());
        assertEquals("external-initial-attempt", frozen.attemptId());
        assertTrue(contract.activeDraft().isEmpty());
        assertEquals(frozen, contract.frozenSnapshots().get("external-initial-attempt"));
        assertThrows(UnsupportedOperationException.class, () -> frozen.document().strokes().clear());
        assertThrows(UnsupportedOperationException.class, () -> frozen.document().strokes().getFirst().points().clear());
        assertThrows(UnsupportedOperationException.class, () -> contract.frozenSnapshots().clear());
        assertThrows(IllegalStateException.class, () -> contract.replaceActiveDraft(DraftCanvasDocument.createEmpty()));
    }

    @Test void failedSubmitLeavesWorkingDraftAndHistoricalValuesUnchanged() {
        var contract = new DraftLifecycleContract(inkDocument("old"));
        var initial = contract.onSubmitSuccess("external-initial");
        contract.onRetryRequested();
        var working = inkDocument("retry-ink");
        contract.replaceActiveDraft(working);
        var historyBefore = contract.frozenSnapshots();
        contract.onSubmitFailure();
        assertSame(working, contract.activeDraft().orElseThrow());
        assertEquals(historyBefore, contract.frozenSnapshots());
        assertSame(initial, contract.frozenSnapshots().get("external-initial"));
    }

    @Test void retryReturnsEmptyAnswerAndFreshCameraCardAndInkWithoutCopyingHistory() {
        var prior = inkDocument("initial-ink");
        var contract = new DraftLifecycleContract(prior);
        var frozen = contract.onSubmitSuccess("external-initial");
        var reset = contract.onRetryRequested();
        assertEquals(Set.of(), reset.selectedOptionIds());
        assertEquals(DraftCanvasDocument.createEmpty(), reset.activeDraft());
        assertEquals(new DraftCanvasDocument.Viewport(0, 0, 1), reset.activeDraft().viewport());
        assertEquals(new DraftCanvasDocument.QuestionCard(120, 70, 720), reset.activeDraft().questionCard());
        assertTrue(reset.activeDraft().strokes().isEmpty());
        assertSame(reset.activeDraft(), contract.activeDraft().orElseThrow());
        assertSame(frozen, contract.frozenSnapshots().get("external-initial"));
        assertEquals(prior, frozen.document());
        assertFalse(frozen.document().strokes().isEmpty());
    }

    @Test void secondSubmissionPreservesPreviousFrozenValueAndStableExternalAssociation() {
        var contract = new DraftLifecycleContract(inkDocument("first"));
        var first = contract.onSubmitSuccess("external-initial");
        contract.onRetryRequested();
        var secondDocument = inkDocument("second");
        contract.replaceActiveDraft(secondDocument);
        var second = contract.onSubmitSuccess("external-retry");
        assertEquals(2, contract.frozenSnapshots().size());
        assertSame(first, contract.frozenSnapshots().get("external-initial"));
        assertSame(second, contract.frozenSnapshots().get("external-retry"));
        assertEquals("first", first.document().strokes().getFirst().id());
        assertEquals("second", second.document().strokes().getFirst().id());
        assertTrue(contract.activeDraft().isEmpty());
    }

    @Test void invalidExternalAssociationsNeverRemoveWorkingDocumentOrRewriteHistory() {
        var contract = new DraftLifecycleContract(inkDocument("first"));
        var initialWorking = contract.activeDraft().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> contract.onSubmitSuccess(" "));
        assertSame(initialWorking, contract.activeDraft().orElseThrow());
        assertTrue(contract.frozenSnapshots().isEmpty());
        assertThrows(IllegalStateException.class, contract::onRetryRequested);
        var first = contract.onSubmitSuccess("external-initial");
        contract.onRetryRequested();
        var retryWorking = contract.activeDraft().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> contract.onSubmitSuccess("external-initial"));
        assertSame(retryWorking, contract.activeDraft().orElseThrow());
        assertSame(first, contract.frozenSnapshots().get("external-initial"));
        assertEquals(1, contract.frozenSnapshots().size());
    }

    private static DraftCanvasDocument inkDocument(String strokeId) {
        return new DraftCanvasDocument("1.0", "1", new DraftCanvasDocument.Viewport(-150, 220, 0.5),
                new DraftCanvasDocument.QuestionCard(210, 95, 600), List.of(new DraftCanvasDocument.Stroke(
                        strokeId, "PEN", "#7054a5", 2.4,
                        List.of(new DraftCanvasDocument.Point(215, 100, 0.5), new DraftCanvasDocument.Point(240, 140, 0.8)))));
    }
}
