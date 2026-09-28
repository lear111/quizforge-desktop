package io.quizforge.core.practice;

import java.time.Instant;
import java.util.Objects;

/** Persisted practice round, independent of the current file and in-memory UI session. */
public record PracticeSession(String id, String questionBankAssetId, String questionBankContentId,
        String bankTitleSnapshot, Status status, View currentView, String currentQuestionId,
        Instant startedAt, Instant lastActivityAt, Instant archivedAt) {
    public enum Status { ACTIVE, ARCHIVED }
    public enum View { QUESTION, SUMMARY }

    public PracticeSession {
        Objects.requireNonNull(id);
        Objects.requireNonNull(questionBankAssetId);
        Objects.requireNonNull(questionBankContentId);
        Objects.requireNonNull(bankTitleSnapshot);
        Objects.requireNonNull(status);
        Objects.requireNonNull(currentView);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(lastActivityAt);
        if (currentView == View.QUESTION && (currentQuestionId == null || currentQuestionId.isBlank())) {
            throw new IllegalArgumentException("QUESTION view requires a question ID.");
        }
        if ((status == Status.ARCHIVED) != (archivedAt != null)) {
            throw new IllegalArgumentException("Only archived sessions require archivedAt.");
        }
    }
}
