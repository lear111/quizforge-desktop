package io.quizforge.core.practice;

import java.time.Instant;
import java.util.Objects;

/** Read-only presentation data for one archived practice round. */
public record PracticeHistoryEntry(String sessionId, Instant startedAt, Instant archivedAt,
        PracticeSummary summary) {
    public PracticeHistoryEntry {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(archivedAt);
        Objects.requireNonNull(summary);
    }
}
