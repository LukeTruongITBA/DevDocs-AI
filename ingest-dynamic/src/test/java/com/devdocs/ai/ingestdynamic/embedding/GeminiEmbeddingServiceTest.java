
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

import java.io.IOException;
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
                3,
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
        mockWebServer.enqueue(jsonResponse("""
                {
                  "embeddings": [
                    {
                      "values": [0.1, 0.2, 0.3]
                    }
                  ]
                }
                """));

        List<float[]> result = service.embed(List.of(chunk(0, "Spring Boot documentation")));

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).length);
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
        mockWebServer.enqueue(jsonResponse("""
                {
                  "embeddings": [
                    {"values": [0.1, 0.2, 0.3]},
                    {"values": [0.4, 0.5, 0.6]}
                  ]
                }
                """));

        mockWebServer.enqueue(jsonResponse("""
                {
                  "embeddings": [
                    {"values": [0.7, 0.8, 0.9]}
                  ]
                }
                """));

        List<float[]> result = service.embed(List.of(
                chunk(0, "Chunk one"),
                chunk(1, "Chunk two"),
                chunk(2, "Chunk three")
        ));

        assertEquals(3, result.size());
        assertEquals(3, result.get(0).length);
        assertEquals(3, result.get(1).length);
        assertEquals(3, result.get(2).length);
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
                3,
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
}
