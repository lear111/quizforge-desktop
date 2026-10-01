package io.quizforge.core.practice;

import java.util.OptionalInt;

/** Current round statistics, derived from question state and immutable attempts. */
public record PracticeSummary(int totalCount, int submittedCount, int correctCount,
        int incorrectCount, int unfinishedCount, OptionalInt accuracyPercent) {
    public static PracticeSummary from(ActivePracticeSnapshot snapshot) {
        return fromQuestions(snapshot.questions());
    }

    public static PracticeSummary fromQuestions(java.util.List<ActivePracticeSnapshot.Question> questions) {
        int correct = 0;
        int incorrect = 0;
        int unscored = 0;
        for (var row : questions) {
            if (row.sessionQuestion().practiceState() != PracticeSessionQuestion.State.SUBMITTED) continue;
            if (row.attempts().isEmpty()) throw new IllegalStateException("Submitted attempt is missing");
            switch (row.attempts().getLast().result()) {
                case CORRECT -> correct++;
                case INCORRECT -> incorrect++;
                case UNSCORED -> unscored++;
            }
        }
        int submitted = correct + incorrect + unscored;
        return new PracticeSummary(questions.size(), submitted, correct, incorrect,
                questions.size() - submitted, submitted == 0 ? OptionalInt.empty()
                        : OptionalInt.of((int) Math.round(100.0 * correct / submitted)));
    }

    public int unscoredCount() { return submittedCount - correctCount - incorrectCount; }
}
