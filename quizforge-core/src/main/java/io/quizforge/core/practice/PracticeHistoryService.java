package io.quizforge.core.practice;

import io.quizforge.core.port.PracticeTransaction;
import java.util.List;

/** Archived practice queries and deletion; no current QBank content is needed. */
public final class PracticeHistoryService {
    private final PracticeTransaction transactions;

    public PracticeHistoryService(PracticeTransaction transactions) { this.transactions = transactions; }

    public List<PracticeHistoryEntry> listArchived(String bankAssetId) {
        if (bankAssetId == null || bankAssetId.isBlank()) throw new IllegalArgumentException("Bank asset ID is required");
        return transactions.execute(repositories -> repositories.sessions().listArchivedByQuestionBankAssetId(bankAssetId)
                .stream().map(session -> {
                    var questions = repositories.questions().findBySessionId(session.id()).stream()
                            .map(question -> new ActivePracticeSnapshot.Question(question,
                                    repositories.attempts().listBySessionQuestion(question.id())))
                            .toList();
                    return new PracticeHistoryEntry(session.id(), session.startedAt(), session.archivedAt(),
                            PracticeSummary.fromQuestions(questions));
                }).toList());
    }

    public void deleteArchivedSession(String bankAssetId, String sessionId) {
        if (bankAssetId == null || bankAssetId.isBlank() || sessionId == null || sessionId.isBlank())
            throw new IllegalArgumentException("Bank asset ID and session ID are required");
        transactions.execute(repositories -> {
            var session = repositories.sessions().findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Practice history was not found"));
            if (!bankAssetId.equals(session.questionBankAssetId()) || session.status() != PracticeSession.Status.ARCHIVED)
                throw new IllegalStateException("Only archived practice for this bank can be deleted");
            repositories.sessions().deleteArchived(sessionId);
            return null;
        });
    }
}
