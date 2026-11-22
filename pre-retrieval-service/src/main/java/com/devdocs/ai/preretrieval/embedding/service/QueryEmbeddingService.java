
package com.devdocs.ai.preretrieval.embedding.service;

import com.devdocs.ai.preretrieval.embedding.config.EmbeddingProperties;
import com.devdocs.ai.preretrieval.embedding.dto.GeminiEmbeddingApiRequest;
import com.devdocs.ai.preretrieval.embedding.dto.GeminiEmbeddingApiResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class QueryEmbeddingService {

    private static final String TASK_TYPE = "RETRIEVAL_QUERY";

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    public QueryEmbeddingService(
            RestClient.Builder restClientBuilder,
            EmbeddingProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public List<Double> embed(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Query must not be blank."
            );
        }

        if (properties.apiKey() == null
                || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is missing. Configure GEMINI_API_KEY."
            );
        }

        GeminiEmbeddingApiRequest.EmbeddingRequest embeddingRequest =
                new GeminiEmbeddingApiRequest.EmbeddingRequest(
                        properties.model(),
                        new GeminiEmbeddingApiRequest.Content(
                                List.of(
                                        new GeminiEmbeddingApiRequest.Part(
                                                query.trim()
                                        )
                                )
                        ),
                        TASK_TYPE,
                        properties.dimensions()
                );

        GeminiEmbeddingApiRequest request =
                new GeminiEmbeddingApiRequest(
                        List.of(embeddingRequest)
                );

        GeminiEmbeddingApiResponse response = restClient.post()
                .uri(properties.apiUrl())
                .header("x-goog-api-key", properties.apiKey())
                .body(request)
                .retrieve()
                .body(GeminiEmbeddingApiResponse.class);

        if (response == null
                || response.embeddings() == null
                || response.embeddings().size() != 1) {
            throw new IllegalStateException(
                    "Gemini returned an unexpected number of embeddings."
            );
        }

        List<Double> vector = response.embeddings()
                .getFirst()
                .values();

        validateVector(vector);

        return List.copyOf(vector);
    }

    private void validateVector(List<Double> vector) {
        if (vector == null
                || vector.size() != properties.dimensions()) {
            throw new IllegalStateException(
                    "Embedding dimension does not match configured dimension "
                            + properties.dimensions() + "."
            );
        }

        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalStateException(
                        "Embedding contains an invalid numeric value."
                );
            }
        }
    }
}
