package io.quizforge.core.port;

import io.quizforge.core.ai.AiRequest;
import io.quizforge.core.ai.AiResponse;

public interface AiProvider {
    String id();

    AiResponse generate(AiRequest request);
}
