package io.quizforge.extensions.ai.deepseek;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.quizforge.extension.ai.AiFailureKind;
import io.quizforge.extension.ai.AiGenerationOptions;
import io.quizforge.extension.ai.AiMessage;
import io.quizforge.extension.ai.AiProviderException;
import io.quizforge.extension.ai.AiRequest;
import io.quizforge.extension.ai.AiRole;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeepSeekAiProviderTest {
    private static final String KEY = UUID.randomUUID().toString();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void serializesRequestAndParsesResponse() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        start(200, "{\"choices\":[{\"message\":{\"content\":\"---\\ntext\"}}]}",
                authorization, requestBody, 0);
        String content = provider(Duration.ofSeconds(2)).generate(request()).content();
        assertEquals("---\ntext", content);
        assertEquals("Bearer " + KEY, authorization.get());
        assertTrue(requestBody.get().contains("\"model\":\"deepseek-v4-flash\""));
        assertTrue(requestBody.get().contains("\"role\":\"user\""));
        assertTrue(requestBody.get().contains("\"content\":\"Hello\""));
        assertFalse(requestBody.get().contains(KEY));
    }

    @Test
    void requestsJsonObjectWithBoundedTokens() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        start(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"questions\\\":[]}\"}}]}",
                new AtomicReference<>(), requestBody, 0);
        provider(Duration.ofSeconds(2)).generate(new AiRequest(
                List.of(new AiMessage(AiRole.USER, "Generate json")),
                new AiGenerationOptions(0.3, true, 8192)));
        assertTrue(requestBody.get().contains("\"response_format\":{\"type\":\"json_object\"}"));
        assertTrue(requestBody.get().contains("\"max_tokens\":8192"));
    }

    @Test
    void mapsAuthenticationErrorsWithoutSecret() throws Exception {
        assertFailure(401, AiFailureKind.AUTHENTICATION_FAILED);
        assertFailure(403, AiFailureKind.AUTHENTICATION_FAILED);
    }

    @Test
    void mapsRateLimitAndServerFailure() throws Exception {
        assertFailure(429, AiFailureKind.RATE_LIMITED);
        assertFailure(503, AiFailureKind.UNAVAILABLE);
    }

    @Test
    void mapsTimeout() throws Exception {
        start(200, "{\"choices\":[]}", new AtomicReference<>(), new AtomicReference<>(), 300);
        AiProviderException error = assertThrows(AiProviderException.class,
                () -> provider(Duration.ofMillis(40)).generate(request()));
        assertEquals(AiFailureKind.TIMEOUT, error.kind());
        assertFalse(error.toString().contains(KEY));
    }

    @Test
    void mapsEmptyAndInvalidResponse() throws Exception {
        start(200, "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}",
                new AtomicReference<>(), new AtomicReference<>(), 0);
        AiProviderException empty = assertThrows(AiProviderException.class,
                () -> provider(Duration.ofSeconds(2)).generate(request()));
        assertEquals(AiFailureKind.INVALID_RESPONSE, empty.kind());
        stop();
        server = null;
        start(200, "not json", new AtomicReference<>(), new AtomicReference<>(), 0);
        AiProviderException invalid = assertThrows(AiProviderException.class,
                () -> provider(Duration.ofSeconds(2)).generate(request()));
        assertEquals(AiFailureKind.INVALID_RESPONSE, invalid.kind());
        stop();
        server = null;
        start(200, "", new AtomicReference<>(), new AtomicReference<>(), 0);
        AiProviderException missing = assertThrows(AiProviderException.class,
                () -> provider(Duration.ofSeconds(2)).generate(request()));
        assertEquals(AiFailureKind.INVALID_RESPONSE, missing.kind());
    }

    @Test
    void mapsNetworkUnavailable() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        var provider = new DeepSeekAiProvider("http://127.0.0.1:" + unusedPort,
                DeepSeekAiProvider.DEFAULT_MODEL, KEY, HttpClient.newHttpClient(),
                Duration.ofSeconds(2));
        AiProviderException error = assertThrows(AiProviderException.class,
                () -> provider.generate(request()));
        assertEquals(AiFailureKind.UNAVAILABLE, error.kind());
        assertFalse(error.toString().contains(KEY));
    }

    private void assertFailure(int status, AiFailureKind kind) throws Exception {
        start(status, "{\"error\":\"" + KEY + "\"}",
                new AtomicReference<>(), new AtomicReference<>(), 0);
        AiProviderException error = assertThrows(AiProviderException.class,
                () -> provider(Duration.ofSeconds(2)).generate(request()));
        assertEquals(kind, error.kind());
        assertFalse(error.toString().contains(KEY));
        stop();
        server = null;
    }

    private void start(int status, String body, AtomicReference<String> authorization,
            AtomicReference<String> requestBody, int delayMillis) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private DeepSeekAiProvider provider(Duration timeout) {
        return new DeepSeekAiProvider("http://127.0.0.1:" + server.getAddress().getPort(),
                DeepSeekAiProvider.DEFAULT_MODEL, KEY, HttpClient.newHttpClient(), timeout);
    }

    private AiRequest request() {
        return new AiRequest(List.of(new AiMessage(AiRole.USER, "Hello")),
                new AiGenerationOptions(0.2));
    }
}
