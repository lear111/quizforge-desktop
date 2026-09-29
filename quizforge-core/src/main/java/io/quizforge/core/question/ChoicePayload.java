package io.quizforge.core.question;

import java.util.List;
public record ChoicePayload(List<ChoiceOption> options) implements QuestionPayload {
    public ChoicePayload { options = List.copyOf(options); }
}
