package io.quizforge.core.question.type.objective.cloze;

import io.quizforge.core.question.model.QuestionAnswerSpec;
import java.util.List;

public record ClozeAnswerSpec(List<Answer> answers) implements QuestionAnswerSpec {
    public record Answer(String blankId,String correctOptionId) { }
    public ClozeAnswerSpec { answers=List.copyOf(answers); }
    public List<String> correctOptionIds(){return answers.stream().map(Answer::correctOptionId).toList();}
}
