package io.quizforge.core.ai;

import java.util.List;
import java.util.Objects;

public record AiRequest(List<AiMessage> messages, AiGenerationOptions options) {
    public AiRequest {
        messages = List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("At least one message is required");
        }
        Objects.requireNonNull(options);
    }
}
