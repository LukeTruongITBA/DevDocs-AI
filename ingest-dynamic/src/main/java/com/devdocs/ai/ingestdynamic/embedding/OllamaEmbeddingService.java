package com.devdocs.ai.ingestdynamic.embedding;

import com.devdocs.ai.ingestdynamic.config.EmbeddingProperties;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fail-closed Ollama client for document embeddings. */
@Service
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "ollama")
public class OllamaEmbeddingService implements EmbeddingService {
    private static final String PREFIX = "search_document: ";

    private final EmbeddingProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingService(
            EmbeddingProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this(properties, restClientBuilder, new ObjectMapper());
    }

    OllamaEmbeddingService(
            EmbeddingProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .requestFactory(timeoutRequestFactory(properties)).build();
        this.objectMapper = objectMapper;
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

    @Override
    public List<float[]> embed(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("Chunks must not be null or empty.");
        }
        if (!properties.isOllama()) {
            throw new IllegalStateException("Ollama embedding is not the configured provider.");
        }

        List<float[]> result = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += properties.batchSize()) {
            int end = Math.min(start + properties.batchSize(), chunks.size());
            result.addAll(embedBatch(chunks.subList(start, end)));
        }
        return List.copyOf(result);
    }

    @Override
    public boolean persistenceReady() {
        // The existing chunks.embedding column is VECTOR(1536). Prompt 12
        // will add the independent VECTOR(768) column before enabling writes.
        return false;
    }

    @Override
    public boolean isOllama() {
        return true;
    }

    private List<float[]> embedBatch(List<TextChunk> batch) {
        List<String> input = batch.stream()
                .map(TextChunk::content)
                .map(this::withPrefix)
                .toList();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.model());
        request.put("input", input);

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.apiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((ignored, clientResponse) -> {
                        int status = clientResponse.getStatusCode().value();
                        if (status < 200 || status >= 300) {
                            throw new IllegalStateException(
                                    "Ollama embedding request failed with HTTP " + status + ".");
                        }
                        try (InputStream body = clientResponse.getBody()) {
                            if (body == null) {
                                throw new IllegalStateException("Ollama returned an empty response.");
                            }
                            return objectMapper.readTree(body.readAllBytes());
                        } catch (IOException exception) {
                            throw new IllegalStateException(
                                    "Ollama returned invalid JSON.", exception);
                        }
                    });
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    hasTimeoutCause(exception)
                            ? "Ollama embedding request timed out."
                            : "Ollama embedding service is unavailable.",
                    exception);
        }

        return parseAndValidate(response, batch.size());
    }

    private boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private List<float[]> parseAndValidate(JsonNode response, int expectedCount) {
        JsonNode embeddings = response == null ? null : response.get("embeddings");
        if (embeddings == null || !embeddings.isArray() || embeddings.size() != expectedCount) {
            throw new IllegalStateException("Ollama returned an unexpected number of embeddings.");
        }
        List<float[]> result = new ArrayList<>();
        for (JsonNode node : embeddings) {
            if (node == null || !node.isArray() || node.size() != properties.dimensions()) {
                throw new IllegalStateException("Ollama returned an embedding with invalid dimensions.");
            }
            float[] vector = new float[node.size()];
            for (int i = 0; i < node.size(); i++) {
                if (!node.get(i).isNumber()) {
                    throw new IllegalStateException("Ollama returned a non-numeric embedding value.");
                }
                float value = node.get(i).floatValue();
                if (!Float.isFinite(value)) {
                    throw new IllegalStateException("Ollama returned a non-finite embedding value.");
                }
                vector[i] = value;
            }
            result.add(vector);
        }
        return List.copyOf(result);
    }

    private String withPrefix(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Chunk content must not be blank.");
        }
        String trimmed = content.trim();
        return trimmed.startsWith(PREFIX) ? trimmed : PREFIX + trimmed;
    }
}
