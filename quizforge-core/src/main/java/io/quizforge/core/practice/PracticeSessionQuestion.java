package io.quizforge.core.practice;

import java.time.Instant;
import java.util.Objects;

/** A question's snapshot and current state within one persisted practice round. */
public record PracticeSessionQuestion(String id, String sessionId, String questionId,
        int questionOrder, Snapshot snapshot, State practiceState, PracticePayload draftAnswer,
        Instant createdAt, Instant updatedAt) {
    public enum State { UNANSWERED, DRAFT, SUBMITTED, RETRYING, REVISING }

    public record Snapshot(String questionType, String stem, PracticePayload options,
            PracticePayload correctAnswer, String analysis, PracticePayload sourceRefs) {
        public Snapshot {
            Objects.requireNonNull(questionType);
            Objects.requireNonNull(stem);
            Objects.requireNonNull(options);
            Objects.requireNonNull(correctAnswer);
            Objects.requireNonNull(analysis);
            Objects.requireNonNull(sourceRefs);
        }
    }

    public PracticeSessionQuestion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(questionId);
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(practiceState);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (questionOrder < 0) throw new IllegalArgumentException("Question order must be nonnegative.");
    }
}
