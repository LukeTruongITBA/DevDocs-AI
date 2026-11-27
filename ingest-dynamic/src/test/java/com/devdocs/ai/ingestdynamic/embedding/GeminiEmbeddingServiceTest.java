
package com.devdocs.ai.ingestdynamic.embedding;

import com.devdocs.ai.ingestdynamic.config.EmbeddingProperties;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GeminiEmbeddingServiceTest {

    private MockWebServer mockWebServer;
    private GeminiEmbeddingService service;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        EmbeddingProperties properties = new EmbeddingProperties(
                mockWebServer.url("/embed").toString(),
                "test-api-key",
                "models/gemini-embedding-001",
                1536,
                2
        );

        service = new GeminiEmbeddingService(
                properties,
                RestClient.builder()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldSendRequestAndReturnEmbedding() throws InterruptedException {
        mockWebServer.enqueue(jsonResponse(responseWithVector(0.1f, 0.2f, 0.3f)));

        List<float[]> result = service.embed(List.of(chunk(0, "Spring Boot documentation")));

        assertEquals(1, result.size());
        assertEquals(1536, result.get(0).length);
        assertEquals(0.1f, result.get(0)[0], 0.0001f);
        assertEquals(0.2f, result.get(0)[1], 0.0001f);
        assertEquals(0.3f, result.get(0)[2], 0.0001f);

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);

        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/embed", request.getPath());
        assertEquals("test-api-key", request.getHeader("x-goog-api-key"));
        assertTrue(request.getBody().readUtf8().contains("Spring Boot documentation"));
    }

    @Test
    void shouldSplitChunksIntoBatches() {
        mockWebServer.enqueue(jsonResponse(responseFor(0.1f, 0.4f)));
        mockWebServer.enqueue(jsonResponse(responseFor(0.7f)));

        List<float[]> result = service.embed(List.of(
                chunk(0, "Chunk one"),
                chunk(1, "Chunk two"),
                chunk(2, "Chunk three")
        ));

        assertEquals(3, result.size());
        assertEquals(1536, result.get(0).length);
        assertEquals(1536, result.get(1).length);
        assertEquals(1536, result.get(2).length);
        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    void shouldRejectEmptyChunks() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.embed(List.of())
        );
    }

    @Test
    void shouldRejectMissingApiKey() {
        EmbeddingProperties properties = new EmbeddingProperties(
                mockWebServer.url("/embed").toString(),
                "",
                "models/gemini-embedding-001",
                1536,
                2
        );

        GeminiEmbeddingService serviceWithoutKey =
                new GeminiEmbeddingService(properties, RestClient.builder());

        assertThrows(
                IllegalStateException.class,
                () -> serviceWithoutKey.embed(List.of(chunk(0, "Test")))
        );

        assertEquals(0, mockWebServer.getRequestCount());
    }

    @Test
    void shouldRejectEmbeddingWithWrongDimensions() {
        mockWebServer.enqueue(jsonResponse("""
                {
                  "embeddings": [
                    {
                      "values": [0.1, 0.2]
                    }
                  ]
                }
                """));

        assertThrows(
                IllegalStateException.class,
                () -> service.embed(List.of(chunk(0, "Test")))
        );
    }

    @Test
    void shouldParseAndSanitizeRateLimitDiagnostics() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "3")
                .setHeader("X-Goog-Request-Id", "request-123")
                .setHeader("X-RateLimit-Remaining", "0")
                .setBody("""
                        {
                          "error": {
                            "status": "RESOURCE_EXHAUSTED",
                            "message": "API key=secret-key token=secret-token"
                          }
                        }
                        """));

        GeminiEmbeddingException exception = assertThrows(
                GeminiEmbeddingException.class,
                () -> service.embed(List.of(chunk(0, "Test")))
        );

        assertEquals(429, exception.statusCode());
        assertEquals("RESOURCE_EXHAUSTED", exception.providerCode());
        assertEquals("3", exception.retryAfter());
        assertEquals(3_000L, exception.retryAfterMillis());
        assertEquals("request-123", exception.requestId());
        assertEquals(
                "0",
                exception.rateLimitHeaders().get("X-RateLimit-Remaining")
        );
        assertTrue(exception.providerMessage().contains("[REDACTED]"));
        assertFalse(exception.getMessage().contains("secret-key"));
        assertFalse(exception.getMessage().contains("secret-token"));
    }

    @Test
    void shouldRetryConfiguredTransientFailureAndRespectRetryAfter() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "0")
                .setBody("{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}"));
        mockWebServer.enqueue(jsonResponse(responseWithVector(0.4f)));
        List<Long> delays = new ArrayList<>();

        GeminiEmbeddingService retryingService = service(
                2,
                100,
                500,
                0,
                delays::add
        );

        List<float[]> result = retryingService.embed(
                List.of(chunk(0, "Retryable"))
        );

        assertEquals(1, result.size());
        assertEquals(0.4f, result.get(0)[0], 0.0001f);
        assertEquals(List.of(0L), delays);
        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    void shouldBoundConfiguredRetryAttempts() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setBody("{\"message\":\"temporarily unavailable\"}"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setBody("{\"message\":\"temporarily unavailable\"}"));

        GeminiEmbeddingService retryingService = service(
                2,
                0,
                0,
                0,
                ignored -> { }
        );

        assertThrows(
                GeminiEmbeddingException.class,
                () -> retryingService.embed(List.of(chunk(0, "Retry")))
        );

        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    void shouldNotRetryPermanentClientErrors() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\":{\"code\":\"INVALID_ARGUMENT\"}}"));

        GeminiEmbeddingService retryingService = service(
                4,
                0,
                0,
                0,
                ignored -> { }
        );

        GeminiEmbeddingException exception = assertThrows(
                GeminiEmbeddingException.class,
                () -> retryingService.embed(List.of(chunk(0, "Invalid")))
        );

        assertFalse(exception.retryable());
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void shouldHandleInterruptedRetryAndRestoreInterruptFlag() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}"));

        GeminiEmbeddingService retryingService = service(
                2,
                10,
                100,
                0,
                ignored -> {
                    throw new InterruptedException("cancelled");
                }
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> retryingService.embed(List.of(chunk(0, "Cancel")))
        );

        assertEquals(
                "Gemini embedding retry was interrupted.",
                exception.getMessage()
        );
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void shouldUseBoundedGenericMessageForUnparseableErrorBody() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("x".repeat(9_000)));

        GeminiEmbeddingException exception = assertThrows(
                GeminiEmbeddingException.class,
                () -> service.embed(List.of(chunk(0, "Malformed")))
        );

        assertEquals(500, exception.statusCode());
        assertEquals(
                "Provider error response could not be parsed.",
                exception.providerMessage()
        );
        assertTrue(exception.getMessage().length() < 500);
    }

    private GeminiEmbeddingService service(
            int maxAttempts,
            long initialBackoffMillis,
            long maxBackoffMillis,
            double jitterRatio,
            GeminiEmbeddingService.RetrySleeper sleeper
    ) {
        EmbeddingProperties properties = new EmbeddingProperties(
                mockWebServer.url("/embed").toString(),
                "test-api-key",
                "models/gemini-embedding-001",
                1536,
                2,
                maxAttempts,
                initialBackoffMillis,
                maxBackoffMillis,
                jitterRatio
        );
        return new GeminiEmbeddingService(
                properties,
                RestClient.builder(),
                new ObjectMapper(),
                sleeper,
                () -> 0.5
        );
    }

    private TextChunk chunk(int index, String content) {
        return new TextChunk(
                index,
                content,
                "https://docs.example.com/spring",
                "Spring Boot Documentation",
                "3.6"
        );
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private String responseFor(float... firstValues) {
        StringBuilder response = new StringBuilder("{\"embeddings\":[");
        for (int i = 0; i < firstValues.length; i++) {
            if (i > 0) response.append(',');
            response.append("{\"values\":[").append(firstValues[i]);
            for (int dimension = 1; dimension < 1536; dimension++) {
                response.append(",0.0");
            }
            response.append("]}");
        }
        return response.append("]}").toString();
    }

    private String responseWithVector(float... initialValues) {
        StringBuilder response = new StringBuilder(
                "{\"embeddings\":[{\"values\":["
        );
        for (int dimension = 0; dimension < 1536; dimension++) {
            if (dimension > 0) response.append(',');
            response.append(dimension < initialValues.length
                    ? initialValues[dimension] : 0.0f);
        }
        return response.append("]}]}").toString();
    }
}
