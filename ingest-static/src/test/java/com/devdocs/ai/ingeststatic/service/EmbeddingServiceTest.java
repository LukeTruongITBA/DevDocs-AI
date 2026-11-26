package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EmbeddingServiceTest {

    @Test
    void shouldSendConfiguredGeminiModelAndDatabaseDimension() {
        String apiUrl = "https://embedding.example.test/batch";
        EmbeddingProperties properties = new EmbeddingProperties(
                apiUrl, "test-only-key", "models/gemini-embedding-001", 1536, 64
        );
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String values = IntStream.range(0, 1536).mapToObj(i -> i == 0 ? "0.25" : "0.0")
                .collect(Collectors.joining(","));
        server.expect(requestTo(apiUrl))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-only-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"outputDimensionality\":1536")))
                .andRespond(withSuccess(
                        "{\"embeddings\":[{\"values\":[" + values + "]}]}",
                        MediaType.APPLICATION_JSON));

        EmbeddingService service = new EmbeddingService(builder, properties);
        var result = service.embed(List.of(new TextChunk(0, 1, "Example chunk", "a".repeat(64))));

        assertEquals(1536, result.getFirst().vector().size());
        assertEquals(0.25, result.getFirst().vector().getFirst());
        server.verify();
    }

    @Test
    void shouldRejectResponseWithWrongVectorDimension() {
        EmbeddingProperties properties = new EmbeddingProperties(
                "https://embedding.example.test/batch", "test-only-key",
                "models/gemini-embedding-001", 1536, 64
        );
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(properties.apiUrl()))
                .andRespond(withSuccess(
                        "{\"embeddings\":[{\"values\":[0.1,0.2]}]}",
                        MediaType.APPLICATION_JSON));

        EmbeddingService service = new EmbeddingService(builder, properties);
        assertThrows(IllegalStateException.class, () -> service.embed(List.of(
                new TextChunk(0, 1, "Example chunk", "a".repeat(64))
        )));
        server.verify();
    }
}
