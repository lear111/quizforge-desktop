package io.quizforge.core.port;

import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSessionQuestion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Snapshot synchronization and archived-session protection belong to the future service layer. */
public interface PracticeSessionQuestionRepository {
    /** The entire batch is inserted atomically. */
    void createAll(List<PracticeSessionQuestion> questions);
    default void create(PracticeSessionQuestion question) { createAll(List.of(question)); }
    List<PracticeSessionQuestion> findBySessionId(String sessionId);
    Optional<PracticeSessionQuestion> findBySessionIdAndQuestionId(String sessionId, String questionId);
    void updateState(String sessionId, String questionId, PracticeSessionQuestion.State state, Instant updatedAt);
    /** Save draft and explicit current state together; never append an attempt. Null clears the draft. */
    void updateDraft(String sessionId, String questionId, PracticePayload draft,
            PracticeSessionQuestion.State state, Instant updatedAt);
    void updateSnapshot(String sessionId, String questionId, int questionOrder,
            PracticeSessionQuestion.Snapshot snapshot, Instant updatedAt);
    /** Reordering must not rewrite the snapshot, draft or attempts. */
    void updateOrder(String sessionId, String questionId, int questionOrder, Instant updatedAt);
    /** Only deletes from an ACTIVE session; attempts are removed by database cascade. */
    void deleteBySessionIdAndQuestionId(String sessionId, String questionId);
}
