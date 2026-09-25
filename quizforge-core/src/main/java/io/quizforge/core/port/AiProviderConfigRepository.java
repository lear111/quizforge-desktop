package io.quizforge.core.port;

import io.quizforge.core.ai.AiProviderConfig;
import java.util.Optional;

public interface AiProviderConfigRepository {
    Optional<AiProviderConfig> findDefault();

    void save(AiProviderConfig config);
}
