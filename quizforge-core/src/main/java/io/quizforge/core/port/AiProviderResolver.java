package io.quizforge.core.port;

import io.quizforge.extension.ai.AiProvider;

public interface AiProviderResolver {
    AiProvider resolve();
}
