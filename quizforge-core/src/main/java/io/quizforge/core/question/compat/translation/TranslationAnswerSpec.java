package io.quizforge.core.question.compat.translation;

import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.model.QuestionAnswerSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TranslationAnswerSpec(List<Answer> answers) implements QuestionAnswerSpec {
    public record Answer(String itemId, QuestionContent referenceAnswer) { }
    public TranslationAnswerSpec { answers = List.copyOf(answers); }
    public Map<String, QuestionContent> referenceAnswers() {
        var result = new LinkedHashMap<String, QuestionContent>();
        answers.forEach(answer -> { if (answer.referenceAnswer() != null) result.put(answer.itemId(), answer.referenceAnswer()); });
        return java.util.Collections.unmodifiableMap(result);
    }
}
