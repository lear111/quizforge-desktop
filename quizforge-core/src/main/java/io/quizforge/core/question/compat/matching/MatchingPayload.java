package io.quizforge.core.question.compat.matching;

import io.quizforge.core.question.model.QuestionPayload;
import java.util.List;

public record MatchingPayload(List<MatchingBlank> blanks, List<MatchingOption> options) implements QuestionPayload {
    public MatchingPayload { blanks = List.copyOf(blanks); options = List.copyOf(options); }
}
