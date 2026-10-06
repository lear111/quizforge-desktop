package io.quizforge.core.question.compat.translation;

import io.quizforge.core.question.model.QuestionPayload;
import java.util.List;

public record TranslationPayload(List<TranslationItem> items) implements QuestionPayload {
    public TranslationPayload { items = List.copyOf(items); }
}
