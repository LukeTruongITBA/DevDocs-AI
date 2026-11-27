
package com.devdocs.ai.preretrieval.embedding.service;

import com.devdocs.ai.preretrieval.embedding.config.EmbeddingProperties;
import com.devdocs.ai.preretrieval.embedding.dto.GeminiEmbeddingApiRequest;
import com.devdocs.ai.preretrieval.embedding.dto.GeminiEmbeddingApiResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class QueryEmbeddingService {

    private static final String TASK_TYPE = "RETRIEVAL_QUERY";

    private final RestClient restClient;
    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueryEmbeddingService(
            RestClient.Builder restClientBuilder,
            EmbeddingProperties properties
    ) {
        this.restClient = properties.isOllama()
                ? configuredBuilder(restClientBuilder, properties).build()
                : restClientBuilder.build();
        this.properties = properties;
    }

    QueryEmbeddingService(RestClient restClient, EmbeddingProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private RestClient.Builder configuredBuilder(
            RestClient.Builder builder,
            EmbeddingProperties properties
    ) {
        SimpleClientHttpRequestFactory factory = timeoutRequestFactory(properties);
        return builder.requestFactory(factory);
    }

    static SimpleClientHttpRequestFactory timeoutRequestFactory(
            EmbeddingProperties properties
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (properties.connectTimeoutMillis() > 0) {
            factory.setConnectTimeout((int) Math.min(
                    Integer.MAX_VALUE, properties.connectTimeoutMillis()));
        }
        if (properties.readTimeoutMillis() > 0) {
            factory.setReadTimeout((int) Math.min(
                    Integer.MAX_VALUE, properties.readTimeoutMillis()));
        }
        return factory;
    }

    public List<Double> embed(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Query must not be blank."
            );
        }

        if (properties.isOllama()) {
            return embedWithOllama(query);
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

    private List<Double> embedWithOllama(String query) {
        String trimmed = query.trim();
        String input = trimmed.startsWith("search_query: ")
                ? trimmed : "search_query: " + trimmed;
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.apiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "model", properties.model(),
                            "input", List.of(input)))
                    .exchange((ignored, clientResponse) -> {
                        int status = clientResponse.getStatusCode().value();
                        if (status < 200 || status >= 300) {
                            throw new IllegalStateException("Ollama embedding request failed with HTTP " + status + ".");
                        }
                        try {
                            if (clientResponse.getBody() == null) {
                                throw new IllegalStateException("Ollama returned an empty response.");
                            }
                            return objectMapper.readTree(clientResponse.getBody().readAllBytes());
                        } catch (java.io.IOException exception) {
                            throw new IllegalStateException("Ollama returned invalid JSON.", exception);
                        }
                    });
        } catch (org.springframework.web.client.RestClientException exception) {
            throw new IllegalStateException(
                    hasTimeoutCause(exception)
                            ? "Ollama embedding request timed out."
                            : "Ollama embedding service is unavailable.",
                    exception);
        }
        JsonNode embeddings = response == null ? null : response.get("embeddings");
        if (embeddings == null || !embeddings.isArray() || embeddings.size() != 1) {
            throw new IllegalStateException("Ollama returned an unexpected number of embeddings.");
        }
        JsonNode node = embeddings.get(0);
        if (node == null || !node.isArray() || node.size() != properties.dimensions()) {
            throw new IllegalStateException("Ollama returned an embedding with invalid dimensions.");
        }
        List<Double> vector = new java.util.ArrayList<>();
        for (JsonNode valueNode : node) {
            if (!valueNode.isNumber() || !Double.isFinite(valueNode.doubleValue())) {
                throw new IllegalStateException("Ollama returned an invalid embedding value.");
            }
            vector.add(valueNode.doubleValue());
        }
        return List.copyOf(vector);
    }

    private boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
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
