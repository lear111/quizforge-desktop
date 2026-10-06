package io.quizforge.core.question.compat.matching;

import io.quizforge.core.question.model.QuestionAnswerSpec;
import java.util.*;

public record MatchingAnswerSpec(List<Answer> answers) implements QuestionAnswerSpec {
    public record Answer(String blankId, String correctOptionId) { }
    public MatchingAnswerSpec { answers = List.copyOf(answers); }
    public Map<String, String> assignments() {
        var result = new LinkedHashMap<String, String>();
        for (var answer : answers) {
            if (result.putIfAbsent(answer.blankId(), answer.correctOptionId()) != null)
                throw new IllegalArgumentException("每个空只能有一个正确答案");
        }
        return Collections.unmodifiableMap(result);
    }
}
