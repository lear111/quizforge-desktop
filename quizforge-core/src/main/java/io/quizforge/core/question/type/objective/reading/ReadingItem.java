package io.quizforge.core.question.type.objective.reading;

import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import java.util.List;

/** Stable subquestion identity is independent of its displayed number. */
public record ReadingItem(String id, int number, QuestionContent prompt, List<ChoiceOption> options) {
    public ReadingItem { options = List.copyOf(options); }
}
