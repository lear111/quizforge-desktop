package io.quizforge.core.practice;

import io.quizforge.core.question.content.QuestionContentData;
import java.math.BigDecimal;
import java.util.Optional;

/** Current round statistics, derived from question state and immutable attempts. */
public record PracticeSummary(int totalCount, int submittedCount, int correctCount,
        int incorrectCount, int unfinishedCount, Optional<BigDecimal> score, Optional<BigDecimal> maxScore) {
    public static PracticeSummary from(ActivePracticeSnapshot snapshot) {
        return fromQuestions(snapshot.questions());
    }

    public static PracticeSummary fromQuestions(java.util.List<ActivePracticeSnapshot.Question> questions) {
        int correct = 0;
        int incorrect = 0;
        int unscored = 0;
        BigDecimal earned=BigDecimal.ZERO,maximum=BigDecimal.ZERO;
        boolean knownScore=true,knownMaximum=true;
        for (var row : questions) {
            var limit=maximum(row);
            if(limit.isPresent())maximum=maximum.add(limit.get());else knownMaximum=false;
            if (row.sessionQuestion().practiceState() != PracticeSessionQuestion.State.SUBMITTED) continue;
            if (row.attempts().isEmpty()) throw new IllegalStateException("Submitted attempt is missing");
            switch (row.attempts().getLast().result()) {
                case CORRECT -> correct++;
                case INCORRECT -> incorrect++;
                case UNSCORED -> unscored++;
            }
            var attempt=row.attempts().getLast();
            if(attempt.result()==QuestionAttempt.Result.UNSCORED)continue;
            if(attempt.score()!=null)earned=earned.add(BigDecimal.valueOf(attempt.score()));
            else if(attempt.result()==QuestionAttempt.Result.CORRECT){
                if(limit.isPresent())earned=earned.add(limit.get());else knownScore=false;
            }
        }
        int submitted = correct + incorrect + unscored;
        return new PracticeSummary(questions.size(), submitted, correct, incorrect,
                questions.size() - submitted,
                knownScore?Optional.of(earned.stripTrailingZeros()):Optional.empty(),
                knownMaximum?Optional.of(maximum.stripTrailingZeros()):Optional.empty());
    }

    /** Legacy archives without a captured score cannot be assigned an invented point value. */
    private static Optional<BigDecimal> maximum(ActivePracticeSnapshot.Question row){
        var fields=QuestionContentData.map(row.sessionQuestion().snapshot().correctAnswer().value());
        Object value=fields.get("maxScore");
        if(value==null)for(String key:java.util.List.of("cloze","reading","matching","translation","essayPresentation")){
            if(fields.get(key) instanceof java.util.Map<?,?> nested && nested.get("maxScore")!=null){value=nested.get("maxScore");break;}
        }
        if(value==null && !row.attempts().isEmpty())value=row.attempts().getLast().maxScore();
        return value==null?Optional.empty():Optional.of(new BigDecimal(value.toString()));
    }

    public int unscoredCount() { return submittedCount - correctCount - incorrectCount; }
}
