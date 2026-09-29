package io.quizforge.core.question;

import java.util.List;
public record ChoiceAnswerSpec(List<String> correctOptionIds) implements QuestionAnswerSpec {
    public ChoiceAnswerSpec { correctOptionIds = List.copyOf(correctOptionIds); }
}
