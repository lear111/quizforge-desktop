package io.quizforge.core.ai;

import java.util.Objects;

public record AiMessage(AiRole role, String content) {
    public AiMessage {
        Objects.requireNonNull(role);
        Objects.requireNonNull(content);
    }
}
