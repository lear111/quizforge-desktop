package io.quizforge.extension.ai;

public interface AiProvider {
    String id();

    AiResponse generate(AiRequest request);
}
