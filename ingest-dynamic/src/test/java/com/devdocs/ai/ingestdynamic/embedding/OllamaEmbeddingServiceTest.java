package com.devdocs.ai.ingestdynamic.embedding;

import com.devdocs.ai.ingestdynamic.config.EmbeddingProperties;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class OllamaEmbeddingServiceTest {
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldApplyConfiguredTimeoutsToOllamaTransport() throws Exception {
        EmbeddingProperties properties = new EmbeddingProperties(
                "https://embedding.example.test/api/embed", "",
                "nomic-embed-text:137m-v1.5-fp16", 768, 64, "ollama");
        var factory = OllamaEmbeddingService.timeoutRequestFactory(properties);
        var connect = factory.getClass().getDeclaredField("connectTimeout");
        var read = factory.getClass().getDeclaredField("readTimeout");
        connect.setAccessible(true);
        read.setAccessible(true);
        assertEquals(5_000, connect.getInt(factory));
        assertEquals(120_000, read.getInt(factory));
    }

    @Test
    void sendsModelAndDocumentPrefixAndPreservesOrder() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"model\":\"nomic-embed-text:137m-v1.5-fp16\",\"embeddings\":[" + vector(0.1) + "," + vector(0.2) + "]}"));
        OllamaEmbeddingService service = service();
        List<TextChunk> chunks = List.of(chunk("one"), chunk("two"));

        List<float[]> result = service.embed(chunks);

        assertEquals(2, result.size());
        assertEquals(0.1f, result.get(0)[0]);
        assertEquals(0.2f, result.get(1)[0]);
        String request = server.takeRequest().getBody().readUtf8();
        assertTrue(request.contains("nomic-embed-text:137m-v1.5-fp16"));
        assertTrue(request.contains("search_document: one"));
        assertFalse(request.contains("search_document: search_document:"));
    }

    @Test
    void rejectsWrongCountAndDoesNotFallback() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"embeddings\":[]}"));
        assertThrows(IllegalStateException.class,
                () -> service().embed(List.of(chunk("one"))));
    }

    @Test
    void handlesProviderFailureClosed() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("quota"));
        assertThrows(IllegalStateException.class,
                () -> service().embed(List.of(chunk("one"))));
    }

    private OllamaEmbeddingService service() {
        return new OllamaEmbeddingService(
                new EmbeddingProperties(
                        server.url("/api/embed").toString(), "",
                        "nomic-embed-text:137m-v1.5-fp16", 768, 64, "ollama"),
                RestClient.builder());
    }

    private TextChunk chunk(String content) {
        return new TextChunk(0, content, "https://example.test", "Example", "1");
    }

    private String vector(double first) {
        return "[" + first + "," + IntStream.range(1, 768)
                .mapToObj(i -> "0.0").collect(Collectors.joining(",")) + "]";
    }
}
