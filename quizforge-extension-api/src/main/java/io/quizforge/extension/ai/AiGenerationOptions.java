package io.quizforge.extension.ai;

public record AiGenerationOptions(double temperature, boolean jsonObject, Integer maxTokens) {
    public AiGenerationOptions(double temperature) {
        this(temperature, false, null);
    }

    public AiGenerationOptions {
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("Temperature must be between 0 and 2");
        }
        if (maxTokens != null && maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
    }
}
