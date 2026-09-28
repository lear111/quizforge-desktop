package io.quizforge.core.practice;

import java.time.Instant;
import java.util.Objects;

/** Immutable submitted answer; repositories expose append/read only. */
public record QuestionAttempt(String id, String sessionQuestionId, int attemptNo,
        Mode attemptMode, PracticePayload answer, Result result, Double score, Double maxScore,
        Instant submittedAt) {
    public enum Mode { INITIAL, REVISION, RETRY }
    public enum Result { CORRECT, INCORRECT, UNSCORED }

    public QuestionAttempt {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sessionQuestionId);
        Objects.requireNonNull(attemptMode);
        Objects.requireNonNull(answer);
        Objects.requireNonNull(result);
        Objects.requireNonNull(submittedAt);
        if (attemptNo < 1) throw new IllegalArgumentException("Attempt number must be positive.");
        if (score != null && !Double.isFinite(score) || maxScore != null && !Double.isFinite(maxScore)) {
            throw new IllegalArgumentException("Scores must be finite when present.");
        }
    }
}
