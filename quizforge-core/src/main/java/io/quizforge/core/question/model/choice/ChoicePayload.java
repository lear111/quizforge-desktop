package io.quizforge.core.question.model.choice;

import io.quizforge.core.question.model.QuestionPayload;
import java.util.List;

public record ChoicePayload(List<ChoiceOption> options) implements QuestionPayload {
    public ChoicePayload { options = List.copyOf(options); }
}
