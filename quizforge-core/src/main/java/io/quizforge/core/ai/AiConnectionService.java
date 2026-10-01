package io.quizforge.core.ai;

import io.quizforge.core.port.AiProviderResolver;
import java.util.List;

public final class AiConnectionService {
    private final AiProviderResolver resolver;

    public AiConnectionService(AiProviderResolver resolver) {
        this.resolver = resolver;
    }

    public void testConnection() {
        try {
            resolver.resolve().generate(new AiRequest(
                    List.of(new AiMessage(AiRole.USER, "Reply with OK.")),
                    new AiGenerationOptions(0)));
        } catch (AiProviderException e) {
            throw AiProviderErrors.map(e);
        }
    }
}
