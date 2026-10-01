package io.quizforge.core.ai;

import java.util.Objects;

public record AiResponse(String content) {
    public AiResponse {
        Objects.requireNonNull(content);
    }
}
