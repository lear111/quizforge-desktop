package io.quizforge.extension.ai;

import java.util.Objects;

public record AiResponse(String content) {
    public AiResponse {
        Objects.requireNonNull(content);
    }
}
