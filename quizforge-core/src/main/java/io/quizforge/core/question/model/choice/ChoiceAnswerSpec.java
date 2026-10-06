package io.quizforge.core.question.model.choice;

import io.quizforge.core.question.model.QuestionAnswerSpec;
import java.util.List;

public record ChoiceAnswerSpec(List<String> correctOptionIds) implements QuestionAnswerSpec {
    public ChoiceAnswerSpec { correctOptionIds = List.copyOf(correctOptionIds); }
}
