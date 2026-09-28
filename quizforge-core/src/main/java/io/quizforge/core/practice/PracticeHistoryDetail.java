package io.quizforge.core.practice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable, file-independent view of one archived practice round. */
public record PracticeHistoryDetail(String sessionId, String bankTitle, Instant startedAt, Instant archivedAt,
        PracticeSummary summary, List<Question> questions) {
    public PracticeHistoryDetail {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(bankTitle);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(archivedAt);
        Objects.requireNonNull(summary);
        questions = List.copyOf(questions);
    }

    public record Question(String sessionQuestionId, String questionId, int questionOrder, String questionType,
            String stem, List<Option> options, List<String> correctOptionIds, String analysis,
            PracticePayload sourceRefs, PracticeSessionQuestion.State finalState, PracticePayload draftAnswer,
            List<Attempt> attempts) {
        public Question {
            options = List.copyOf(options);
            correctOptionIds = List.copyOf(correctOptionIds);
            attempts = List.copyOf(attempts);
            Objects.requireNonNull(sourceRefs);
            Objects.requireNonNull(finalState);
        }
    }

    public record Option(String id, String content) { }

    public record Attempt(int attemptNo, QuestionAttempt.Mode mode, PracticePayload answer,
            QuestionAttempt.Result result, Double score, Double maxScore, Instant submittedAt) { }
}
