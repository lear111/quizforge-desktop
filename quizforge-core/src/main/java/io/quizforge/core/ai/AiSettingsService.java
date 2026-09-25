package io.quizforge.core.ai;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.AiProviderConfigRepository;
import io.quizforge.core.port.CredentialStore;
import java.net.URI;
import java.time.Clock;
import java.util.Optional;

public final class AiSettingsService {
    public static final String DEFAULT_ID = "default";
    private final AiProviderConfigRepository repository;
    private final CredentialStore credentials;
    private final Clock clock;

    public AiSettingsService(AiProviderConfigRepository repository,
            CredentialStore credentials, Clock clock) {
        this.repository = repository;
        this.credentials = credentials;
        this.clock = clock;
    }

    public Optional<AiProviderConfig> configuration() {
        return repository.findDefault();
    }

    public boolean hasCredential() {
        return configuration().map(config -> credentials.exists(config.credentialRef())).orElse(false);
    }

    public AiProviderConfig save(String providerType, String baseUrl, String model, String newApiKey) {
        String provider = required(providerType, "Provider");
        String url = required(baseUrl, "Base URL");
        String modelName = required(model, "Model");
        if (!provider.matches("[a-z0-9-]{1,40}")) {
            throw new IllegalArgumentException("Invalid AI provider ID.");
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !"https".equalsIgnoreCase(uri.getScheme())
                    && !("http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost())))) {
                throw new IllegalArgumentException("Base URL must use HTTPS (or local HTTP).");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid AI provider Base URL.");
        }
        String reference = "ai-provider-" + provider;
        AiProviderConfig config = new AiProviderConfig(DEFAULT_ID, provider, url, modelName,
                reference, clock.instant());
        if (newApiKey != null && !newApiKey.isBlank()) {
            credentials.save(reference, newApiKey.trim());
        }
        repository.save(config);
        return config;
    }

    public void removeApiKey() {
        AiProviderConfig config = configuration().orElseThrow(() ->
                new QuizForgeException(ErrorCode.AI_PROVIDER_NOT_CONFIGURED,
                        "Configure an AI Provider first."));
        credentials.delete(config.credentialRef());
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value.trim();
    }
}
