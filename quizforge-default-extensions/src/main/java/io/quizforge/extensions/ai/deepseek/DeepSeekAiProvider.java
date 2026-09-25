package io.quizforge.extensions.ai.deepseek;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.extension.ai.AiFailureKind;
import io.quizforge.extension.ai.AiMessage;
import io.quizforge.extension.ai.AiProvider;
import io.quizforge.extension.ai.AiProviderException;
import io.quizforge.extension.ai.AiRequest;
import io.quizforge.extension.ai.AiResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public final class DeepSeekAiProvider implements AiProvider {
    public static final String ID = "deepseek";
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final Duration timeout;

    public DeepSeekAiProvider(String baseUrl, String model, String apiKey) {
        this(baseUrl, model, apiKey, HttpClient.newHttpClient(), Duration.ofSeconds(90));
    }

    public DeepSeekAiProvider(String baseUrl, String model, String apiKey,
            HttpClient client, Duration timeout) {
        URI base;
        try {
            base = URI.create(Objects.requireNonNull(baseUrl).trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid AI provider base URL");
        }
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (!("https".equals(scheme) || ("http".equals(scheme)
                && ("localhost".equalsIgnoreCase(base.getHost())
                || "127.0.0.1".equals(base.getHost()))))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalArgumentException("AI provider base URL must use HTTPS (or local HTTP)");
        }
        endpoint = URI.create(base.toString().replaceAll("/+$", "") + "/chat/completions");
        this.model = Objects.requireNonNull(model).trim();
        this.apiKey = Objects.requireNonNull(apiKey).trim();
        this.client = Objects.requireNonNull(client);
        this.timeout = Objects.requireNonNull(timeout);
        if (this.model.isEmpty() || this.apiKey.isEmpty() || this.apiKey.contains("\n")
                || this.apiKey.contains("\r") || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Model, API key and positive timeout are required");
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        try {
            ObjectNode payload = JSON.createObjectNode();
            payload.put("model", model);
            payload.put("temperature", request.options().temperature());
            ArrayNode messages = payload.putArray("messages");
            for (AiMessage message : request.messages()) {
                ObjectNode item = messages.addObject();
                item.put("role", message.role().name().toLowerCase(Locale.ROOT));
                item.put("content", message.content());
            }
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw failure(AiFailureKind.AUTHENTICATION_FAILED, status);
            }
            if (status == 429) {
                throw failure(AiFailureKind.RATE_LIMITED, status);
            }
            if (status >= 500) {
                throw failure(AiFailureKind.UNAVAILABLE, status);
            }
            if (status < 200 || status >= 300) {
                throw failure(AiFailureKind.INVALID_RESPONSE, status);
            }
            JsonNode body;
            try {
                body = JSON.readTree(response.body());
            } catch (JsonProcessingException e) {
                throw new AiProviderException(AiFailureKind.INVALID_RESPONSE,
                        "AI provider returned invalid JSON.");
            }
            JsonNode content = body == null ? MissingNode.getInstance()
                    : body.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new AiProviderException(AiFailureKind.INVALID_RESPONSE,
                        "AI provider returned empty content.");
            }
            return new AiResponse(content.asText());
        } catch (HttpTimeoutException e) {
            throw new AiProviderException(AiFailureKind.TIMEOUT, "AI provider request timed out.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiFailureKind.UNAVAILABLE, "AI provider request was interrupted.");
        } catch (IOException e) {
            throw new AiProviderException(AiFailureKind.UNAVAILABLE, "AI provider is unavailable.");
        }
    }

    private AiProviderException failure(AiFailureKind kind, int status) {
        return new AiProviderException(kind, "AI provider HTTP status " + status + ".");
    }
}
