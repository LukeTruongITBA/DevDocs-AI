package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.ChunkEmbedding;
import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.config.EmbeddingProperties;
import com.devdocs.ai.ingeststatic.dto.GeminiEmbeddingApiRequest;
import com.devdocs.ai.ingeststatic.dto.GeminiEmbeddingApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private static final String TASK_TYPE = "RETRIEVAL_DOCUMENT";

    private final RestClient restClient;
    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public EmbeddingService(
            RestClient.Builder restClientBuilder,
            EmbeddingProperties properties
    ) {
        this.restClient = properties.isOllama()
                ? configuredBuilder(restClientBuilder, properties).build()
                : restClientBuilder.build();
        this.properties = properties;
    }

    EmbeddingService(RestClient restClient, EmbeddingProperties properties) {
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

    public List<ChunkEmbedding> embed(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chunks must not be empty."
            );
        }

        if (properties.isOllama()) {
            return embedWithOllama(chunks);
        }

        if (properties.apiKey() == null
                || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is missing. Configure GEMINI_API_KEY."
            );
        }

        List<ChunkEmbedding> embeddings = new ArrayList<>();

        for (int start = 0; start < chunks.size();
             start += properties.batchSize()) {

            int end = Math.min(
                    start + properties.batchSize(),
                    chunks.size()
            );

            List<TextChunk> batch = chunks.subList(start, end);

            List<GeminiEmbeddingApiRequest.EmbeddingRequest> requests =
                    batch.stream()
                            .map(chunk -> {
                                if (chunk.content() == null
                                        || chunk.content().isBlank()) {
                                    throw new IllegalArgumentException(
                                            "Chunk content must not be blank."
                                    );
                                }

                                return new GeminiEmbeddingApiRequest.EmbeddingRequest(
                                        properties.model(),
                                        new GeminiEmbeddingApiRequest.Content(
                                                List.of(
                                                        new GeminiEmbeddingApiRequest.Part(
                                                                chunk.content()
                                                        )
                                                )
                                        ),
                                        TASK_TYPE,
                                        properties.dimensions()
                                );
                            })
                            .toList();

            GeminiEmbeddingApiRequest request =
                    new GeminiEmbeddingApiRequest(requests);

            GeminiEmbeddingApiResponse response = restClient.post()
                    .uri(properties.apiUrl())
                    .header("x-goog-api-key", properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(GeminiEmbeddingApiResponse.class);

            if (response == null
                    || response.embeddings() == null
                    || response.embeddings().size() != batch.size()) {
                throw new IllegalStateException(
                        "Gemini returned an unexpected number of embeddings."
                );
            }

            for (int i = 0; i < batch.size(); i++) {
                List<Double> vector = response.embeddings()
                        .get(i)
                        .values();

                validateVector(vector);

                embeddings.add(new ChunkEmbedding(
                        batch.get(i).chunkIndex(),
                        List.copyOf(vector)
                ));
            }
        }

        return List.copyOf(embeddings);
    }

    private List<ChunkEmbedding> embedWithOllama(List<TextChunk> chunks) {
        List<ChunkEmbedding> result = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += properties.batchSize()) {
            int end = Math.min(start + properties.batchSize(), chunks.size());
            List<TextChunk> batch = chunks.subList(start, end);
            List<String> input = batch.stream().map(TextChunk::content)
                    .map(this::documentInput).toList();
            JsonNode response;
            try {
                response = restClient.post()
                        .uri(properties.apiUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(java.util.Map.of("model", properties.model(), "input", input))
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
            if (embeddings == null || !embeddings.isArray() || embeddings.size() != batch.size()) {
                throw new IllegalStateException("Ollama returned an unexpected number of embeddings.");
            }
            List<ChunkEmbedding> validated = new ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++) {
                JsonNode node = embeddings.get(i);
                if (node == null || !node.isArray() || node.size() != properties.dimensions()) {
                    throw new IllegalStateException("Ollama returned an embedding with invalid dimensions.");
                }
                List<Double> vector = new ArrayList<>();
                for (JsonNode valueNode : node) {
                    if (!valueNode.isNumber()) {
                        throw new IllegalStateException("Ollama returned a non-numeric embedding value.");
                    }
                    double value = valueNode.doubleValue();
                    if (!Double.isFinite(value)) {
                        throw new IllegalStateException("Ollama returned a non-finite embedding value.");
                    }
                    vector.add(value);
                }
                validated.add(new ChunkEmbedding(batch.get(i).chunkIndex(), List.copyOf(vector)));
            }
            result.addAll(validated);
        }
        return List.copyOf(result);
    }

    private boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private String documentInput(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Chunk content must not be blank.");
        }
        String trimmed = content.trim();
        return trimmed.startsWith("search_document: ")
                ? trimmed : "search_document: " + trimmed;
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
