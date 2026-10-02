package io.quizforge.core.question.type.objective.cloze;

import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import java.util.List;

/** A stable blank identity and its user-visible marker number. */
public record ClozeBlank(String id, int number, List<ChoiceOption> options) {
    public ClozeBlank { options=List.copyOf(options); }
}
