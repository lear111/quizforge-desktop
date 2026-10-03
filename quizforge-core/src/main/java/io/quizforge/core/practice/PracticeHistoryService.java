package io.quizforge.core.practice;

import io.quizforge.core.port.PracticeTransaction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    public PracticeHistoryDetail loadArchivedSessionDetail(String bankAssetId, String sessionId) {
        if (bankAssetId == null || bankAssetId.isBlank() || sessionId == null || sessionId.isBlank())
            throw new IllegalArgumentException("Bank asset ID and session ID are required");
        return transactions.execute(repositories -> {
            var session = repositories.sessions().findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Practice history was not found"));
            if (session.status() != PracticeSession.Status.ARCHIVED
                    || !bankAssetId.equals(session.questionBankAssetId()))
                throw new IllegalStateException("Archived practice for this bank is unavailable");
            var rows = repositories.questions().findBySessionId(sessionId).stream()
                    .sorted(Comparator.comparingInt(PracticeSessionQuestion::questionOrder)).map(question ->
                            new ActivePracticeSnapshot.Question(question,
                                    repositories.attempts().listBySessionQuestion(question.id()).stream()
                                            .sorted(Comparator.comparingInt(QuestionAttempt::attemptNo)).toList()))
                    .toList();
            var questions = rows.stream().map(row -> {
                var question = row.sessionQuestion();
                var snapshot = question.snapshot();
                var attempts = row.attempts().stream().map(attempt -> new PracticeHistoryDetail.Attempt(
                        attempt.attemptNo(), attempt.attemptMode(), attempt.answer(), attempt.result(),
                        attempt.score(), attempt.maxScore(), attempt.submittedAt(), attempt.id())).toList();
                return new PracticeHistoryDetail.Question(question.id(), question.questionId(),
                        question.questionOrder(), snapshot.questionType(), snapshot.stem(),
                        options(snapshot.options()), correctIds(snapshot.correctAnswer()), snapshot.analysis(),
                        snapshot.sourceRefs(), question.practiceState(), question.draftAnswer(), attempts,snapshot.correctAnswer());
            }).toList();
            return new PracticeHistoryDetail(session.id(), session.bankTitleSnapshot(), session.startedAt(),
                    session.archivedAt(), PracticeSummary.fromQuestions(rows), questions,session.questionBankContentId());
        });
    }

    private static List<PracticeHistoryDetail.Option> options(PracticePayload payload) {
        if (!(payload.value() instanceof List<?> values)) throw new IllegalStateException("Invalid option snapshot");
        List<PracticeHistoryDetail.Option> options = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> option) || !(option.get("id") instanceof String id)
                    || !(option.get("content") instanceof String content))
                throw new IllegalStateException("Invalid option snapshot");
            options.add(new PracticeHistoryDetail.Option(id, content));
        }
        return List.copyOf(options);
    }

    /** Read-only archived projection. Never accesses the mutable Active Draft repository. */
    public HistoryDraftReplay loadDraftReplay(String bankAssetId, String sessionId,
            String sessionQuestionId, String attemptId) {
        if (attemptId == null || attemptId.isBlank()) return HistoryDraftReplay.missing();
        return transactions.execute(repositories -> {
            var session = repositories.sessions().findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Practice history was not found"));
            if (session.status() != PracticeSession.Status.ARCHIVED || !bankAssetId.equals(session.questionBankAssetId()))
                throw new IllegalStateException("Archived practice for this bank is unavailable");
            var question = repositories.questions().findBySessionId(sessionId).stream()
                    .filter(row -> row.id().equals(sessionQuestionId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("History question was not found"));
            if (repositories.attempts().listBySessionQuestion(question.id()).stream().noneMatch(attempt -> attempt.id().equals(attemptId)))
                throw new IllegalArgumentException("Attempt does not belong to this history question");
            try {
                return repositories.draftSnapshots().find(attemptId)
                        .map(snapshot -> HistoryDraftReplay.ready(snapshot.document()))
                        .orElseGet(HistoryDraftReplay::missing);
            } catch (IllegalArgumentException malformedOrUnsupported) {
                return HistoryDraftReplay.unavailable("此草稿使用当前版本暂不支持的格式，或内容已损坏。");
            }
        });
    }

    private static List<String> correctIds(PracticePayload payload) {
        if (!(payload.value() instanceof Map<?, ?> answer)
                || !(answer.get("correctOptionIds") instanceof List<?> values))
            throw new IllegalStateException("Invalid correct answer snapshot");
        List<String> ids = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String id)) throw new IllegalStateException("Invalid correct answer snapshot");
            ids.add(id);
        }
        return List.copyOf(ids);
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
