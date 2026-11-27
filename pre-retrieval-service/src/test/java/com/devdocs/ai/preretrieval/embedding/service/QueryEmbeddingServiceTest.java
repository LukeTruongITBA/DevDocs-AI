
package com.devdocs.ai.preretrieval.embedding.service;

import com.devdocs.ai.preretrieval.embedding.config.EmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class QueryEmbeddingServiceTest {

    @Test
    void shouldApplyConfiguredTimeoutsToOllamaTransport() throws Exception {
        EmbeddingProperties properties = new EmbeddingProperties(
                API_URL, "", "nomic-embed-text:137m-v1.5-fp16", 768, 64, "ollama");
        var factory = QueryEmbeddingService.timeoutRequestFactory(properties);
        var connect = factory.getClass().getDeclaredField("connectTimeout");
        var read = factory.getClass().getDeclaredField("readTimeout");
        connect.setAccessible(true);
        read.setAccessible(true);
        assertEquals(5_000, connect.getInt(factory));
        assertEquals(120_000, read.getInt(factory));
    }

    private static final String API_URL =
            "https://example.com/embedding";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private QueryEmbeddingService service;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer
                .bindTo(restClientBuilder)
                .build();

        service = new QueryEmbeddingService(
                restClientBuilder,
                new EmbeddingProperties(
                        API_URL,
                        "test-api-key",
                        "models/gemini-embedding-001",
                        3,
                        64
                )
        );
    }

    @Test
    void shouldUseOllamaQueryPrefixAnd768Dimensions() {
        EmbeddingProperties properties = new EmbeddingProperties(
                API_URL, "", "nomic-embed-text:137m-v1.5-fp16", 768, 64, "ollama");
        QueryEmbeddingService ollama = new QueryEmbeddingService(restClientBuilder.build(), properties);
        String values = IntStream.range(0, 768).mapToObj(i -> i == 0 ? "0.7" : "0.0")
                .collect(Collectors.joining(","));
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("search_query: spring boot")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nomic-embed-text:137m-v1.5-fp16")))
                .andRespond(withSuccess("{\"embeddings\":[[" + values + "]]}", MediaType.APPLICATION_JSON));

        List<Double> result = ollama.embed("  spring boot  ");

        assertEquals(768, result.size());
        assertEquals(0.7, result.getFirst());
        mockServer.verify();
    }

    @Test
    void shouldReturnQueryEmbeddingWhenRequestIsValid() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(POST))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andExpect(content().json("""
                        {
                          "requests": [
                            {
                              "model": "models/gemini-embedding-001",
                              "content": {
                                "parts": [
                                  {
                                    "text": "spring boot configuration"
                                  }
                                ]
                              },
                              "taskType": "RETRIEVAL_QUERY",
                              "outputDimensionality": 3
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "embeddings": [
                            {
                              "values": [0.1, 0.2, 0.3]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<Double> result = service.embed(
                "  spring boot configuration  "
        );

        assertEquals(List.of(0.1, 0.2, 0.3), result);

        mockServer.verify();
    }

    @Test
    void shouldRejectBlankQuery() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.embed("   ")
        );

        mockServer.verify();
    }

    @Test
    void shouldRejectMissingApiKey() {
        QueryEmbeddingService serviceWithoutApiKey =
                new QueryEmbeddingService(
                        restClientBuilder,
                        new EmbeddingProperties(
                                API_URL,
                                "",
                                "models/gemini-embedding-001",
                                3,
                                64
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () -> serviceWithoutApiKey.embed("spring boot")
        );

        mockServer.verify();
    }

    @Test
    void shouldRejectUnexpectedNumberOfEmbeddings() {
        mockServer.expect(requestTo(API_URL))
                .andRespond(withSuccess("""
                        {
                          "embeddings": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThrows(
                IllegalStateException.class,
                () -> service.embed("spring boot")
        );

        mockServer.verify();
    }

    @Test
    void shouldRejectEmbeddingWithWrongDimensions() {
        mockServer.expect(requestTo(API_URL))
                .andRespond(withSuccess("""
                        {
                          "embeddings": [
                            {
                              "values": [0.1, 0.2]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThrows(
                IllegalStateException.class,
                () -> service.embed("spring boot")
        );

        mockServer.verify();
    }
}
