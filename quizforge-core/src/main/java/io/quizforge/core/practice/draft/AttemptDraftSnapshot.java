package io.quizforge.core.practice.draft;

import java.time.Instant;
import java.util.Objects;

/** Append-only historical geometry owned by an immutable Attempt. */
public record AttemptDraftSnapshot(String attemptId, DraftCanvasDocument document, Instant createdAt) {
    public AttemptDraftSnapshot {
        if (attemptId == null || attemptId.isBlank()) throw new IllegalArgumentException("Attempt ID required");
        Objects.requireNonNull(document); Objects.requireNonNull(createdAt);
    }
}
