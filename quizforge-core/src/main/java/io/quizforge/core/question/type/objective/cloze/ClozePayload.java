package io.quizforge.core.question.type.objective.cloze;

import io.quizforge.core.question.model.QuestionPayload;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import java.util.List;

public record ClozePayload(List<ClozeBlank> blanks) implements QuestionPayload {
    public ClozePayload { blanks=List.copyOf(blanks); }
    public List<ChoiceOption> options(){return blanks.stream().flatMap(b->b.options().stream()).toList();}
}
