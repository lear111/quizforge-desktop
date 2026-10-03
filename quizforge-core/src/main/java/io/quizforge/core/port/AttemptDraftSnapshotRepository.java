package io.quizforge.core.port;

import io.quizforge.core.practice.draft.AttemptDraftSnapshot;
import java.util.Optional;

/** Deliberately no update or delete: historical facts may only be appended/read. */
public interface AttemptDraftSnapshotRepository {
    Optional<AttemptDraftSnapshot> find(String attemptId);
    void append(AttemptDraftSnapshot snapshot);
}
