package io.quizforge.core.practice.draft;

import java.time.Instant;
import java.util.Objects;

/** Replaceable working state owned by a session question. */
public record ActiveDraftCanvas(String sessionQuestionId, DraftCanvasDocument document, Instant updatedAt) {
    public ActiveDraftCanvas {
        if (sessionQuestionId == null || sessionQuestionId.isBlank()) throw new IllegalArgumentException("Session question ID required");
        Objects.requireNonNull(document); Objects.requireNonNull(updatedAt);
    }
}
