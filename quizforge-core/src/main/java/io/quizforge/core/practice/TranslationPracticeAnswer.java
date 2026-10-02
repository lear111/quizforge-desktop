package io.quizforge.core.practice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Independent, self-contained rich answers keyed by translation sentence identity. */
public record TranslationPracticeAnswer(Map<String, EssayPracticeAnswer> answers) {
    public TranslationPracticeAnswer {
        var values = new LinkedHashMap<String, EssayPracticeAnswer>();
        answers.forEach((id, answer) -> {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Invalid translation item ID");
            Objects.requireNonNull(answer);
            if (!answer.empty()) values.put(id, answer);
        });
        answers = Map.copyOf(values);
    }
    public boolean empty() { return answers.isEmpty(); }
    public PracticePayload payload() {
        var values = new LinkedHashMap<String, Object>();
        answers.forEach((id, answer) -> values.put(id, answer.payload().value()));
        return new PracticePayload(values);
    }
    public static TranslationPracticeAnswer from(PracticePayload payload) {
        if (payload == null) return new TranslationPracticeAnswer(Map.of());
        if (!(payload.value() instanceof Map<?, ?> values))
            throw new IllegalArgumentException("Translation answer must be an item-to-answer map");
        var answers = new LinkedHashMap<String, EssayPracticeAnswer>();
        values.forEach((key, value) -> {
            if (!(key instanceof String id)) throw new IllegalArgumentException("Invalid translation item ID");
            answers.put(id, EssayPracticeAnswer.from(new PracticePayload(value)));
        });
        return new TranslationPracticeAnswer(answers);
    }
}
