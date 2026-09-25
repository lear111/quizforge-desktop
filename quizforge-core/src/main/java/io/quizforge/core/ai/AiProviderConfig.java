package io.quizforge.core.ai;

import java.time.Instant;

public record AiProviderConfig(String id, String providerType, String baseUrl,
        String model, String credentialRef, Instant updatedAt) {
}
