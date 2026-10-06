package io.quizforge.core.question.compat.reading;

import io.quizforge.core.question.model.QuestionAnswerSpec;
import java.util.List;

public record ReadingAnswerSpec(List<Answer> answers) implements QuestionAnswerSpec {
    public record Answer(String itemId, String correctOptionId) { }
    public ReadingAnswerSpec { answers = List.copyOf(answers); }
    public List<String> correctOptionIds() { return answers.stream().map(Answer::correctOptionId).toList(); }
}
