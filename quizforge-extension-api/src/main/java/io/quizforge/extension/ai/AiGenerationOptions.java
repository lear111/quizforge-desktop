package io.quizforge.extension.ai;

public record AiGenerationOptions(double temperature) {
    public AiGenerationOptions {
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("Temperature must be between 0 and 2");
        }
    }
}
