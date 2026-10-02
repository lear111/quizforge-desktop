package io.quizforge.core.question.type.objective.reading;

import io.quizforge.core.question.model.QuestionPayload;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import java.util.List;

public record ReadingPayload(List<ReadingItem> items) implements QuestionPayload {
    public ReadingPayload { items = List.copyOf(items); }
    public List<ChoiceOption> options() { return items.stream().flatMap(item -> item.options().stream()).toList(); }
}
