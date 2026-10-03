package io.quizforge.desktop.poc.draftcanvas.contract;

import io.quizforge.core.practice.draft.DraftCanvasDocument;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Executable future lifecycle specification only; deliberately not wired to the live POC.
 * Core owns submit success/failure and Attempt IDs. This class neither grades nor writes storage.
 * REVISION is distinct from RETRY and is intentionally not implemented here.
 */
public final class DraftLifecycleContract {
    public record FrozenSnapshot(String attemptId, DraftCanvasDocument document) {
        public FrozenSnapshot {
            if (attemptId == null || attemptId.isBlank()) throw new IllegalArgumentException("An external Attempt ID is required");
            Objects.requireNonNull(document);
        }
    }
    /** The future coordinator applies this empty answer to Core; the spec never mutates Core itself. */
    public record RetryReset(Set<String> selectedOptionIds, DraftCanvasDocument activeDraft) {
        public RetryReset { selectedOptionIds = Set.copyOf(selectedOptionIds); Objects.requireNonNull(activeDraft); }
    }

    // The working slot is mutable; every document value is deeply immutable.
    private DraftCanvasDocument activeDraft;
    private final Map<String, FrozenSnapshot> frozen = new LinkedHashMap<>();

    public DraftLifecycleContract() { this(DraftCanvasDocument.createEmpty()); }
    public DraftLifecycleContract(DraftCanvasDocument initial) { activeDraft = Objects.requireNonNull(initial); }

    public Optional<DraftCanvasDocument> activeDraft() { return Optional.ofNullable(activeDraft); }
    public Map<String, FrozenSnapshot> frozenSnapshots() { return Map.copyOf(frozen); }

    public void replaceActiveDraft(DraftCanvasDocument document) {
        if (activeDraft == null) throw new IllegalStateException("There is no active working draft");
        activeDraft = Objects.requireNonNull(document);
    }

    /** Called only after the existing Core operation has successfully produced this external Attempt. */
    public FrozenSnapshot onSubmitSuccess(String externalAttemptId) {
        if (activeDraft == null) throw new IllegalStateException("There is no active working draft to freeze");
        if (frozen.containsKey(externalAttemptId)) throw new IllegalArgumentException("Attempt snapshot already exists");
        var snapshot = new FrozenSnapshot(externalAttemptId, activeDraft);
        frozen.put(externalAttemptId, snapshot);
        activeDraft = null;
        return snapshot;
    }

    /** An unsuccessful Core operation changes neither the working document nor any frozen value. */
    public void onSubmitFailure() { }

    /** Future RETRY starts a fresh camera/card and empty ink; it never copies a prior snapshot. */
    public RetryReset onRetryRequested() {
        if (activeDraft != null || frozen.isEmpty())
            throw new IllegalStateException("Retry requires a frozen submission and no active working draft");
        activeDraft = DraftCanvasDocument.createEmpty();
        return new RetryReset(Set.of(), activeDraft);
    }
}
