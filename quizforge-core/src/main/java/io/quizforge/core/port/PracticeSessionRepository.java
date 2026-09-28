package io.quizforge.core.port;

import io.quizforge.core.practice.PracticeSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PracticeSessionRepository {
    void create(PracticeSession session);
    Optional<PracticeSession> findById(String sessionId);
    Optional<PracticeSession> findActiveByQuestionBankAssetId(String questionBankAssetId);
    List<PracticeSession> listArchivedByQuestionBankAssetId(String questionBankAssetId);
    void updateCurrentPosition(String sessionId, PracticeSession.View view, String questionId);
    void touch(String sessionId, Instant lastActivityAt);
    void archive(String sessionId, Instant archivedAt);
    void deleteArchived(String sessionId);
}
