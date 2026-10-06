package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.practice.PracticePayload;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads option ID lists from historical non-extension attempts; never executes a question type. */
final class LegacyChoiceAnswerDecoder {
    private LegacyChoiceAnswerDecoder() { }

    static Set<String> answerIds(PracticePayload payload) {
        if (!(payload.value() instanceof List<?> values))
            throw new IllegalStateException("Choice answer must be an option ID list");
        Set<String> ids = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String id) || !ids.add(id))
                throw new IllegalStateException("Invalid choice option IDs");
        }
        return Set.copyOf(ids);
    }
}
