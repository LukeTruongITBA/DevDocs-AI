
package com.devdocs.ai.ingestdynamic.embedding;

import com.devdocs.ai.ingestdynamic.config.EmbeddingProperties;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiEmbeddingService {

    private final EmbeddingProperties properties;
    private final RestClient restClient;

    public GeminiEmbeddingService(
            EmbeddingProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public List<float[]> embed(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chunks must not be null or empty."
            );
        }

        if (properties.apiKey() == null
                || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is not configured."
            );
        }

        List<float[]> embeddings = new ArrayList<>();

        for (int start = 0;
             start < chunks.size();
             start += properties.batchSize()) {

            int end = Math.min(
                    start + properties.batchSize(),
                    chunks.size()
            );

            List<TextChunk> batch = chunks.subList(start, end);

            embeddings.addAll(embedBatch(batch));
        }

        return List.copyOf(embeddings);
    }

    private List<float[]> embedBatch(List<TextChunk> batch) {
        List<Map<String, Object>> requests = new ArrayList<>();

        for (TextChunk chunk : batch) {
            Map<String, Object> content = Map.of(
                    "parts",
                    List.of(Map.of("text", chunk.content()))
            );

            Map<String, Object> request = new HashMap<>();
            request.put("model", properties.model());
            request.put("content", content);
            request.put(
                    "outputDimensionality",
                    properties.dimensions()
            );

            requests.add(request);
        }

        Map<String, Object> requestBody = Map.of(
                "requests",
                requests
        );

        JsonNode response = restClient.post()
                .uri(properties.apiUrl())
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null
                || !response.has("embeddings")
                || !response.get("embeddings").isArray()) {
            throw new IllegalStateException(
                    "Gemini returned an invalid embedding response."
            );
        }

        JsonNode embeddingNodes = response.get("embeddings");

        if (embeddingNodes.size() != batch.size()) {
            throw new IllegalStateException(
                    "Gemini returned an unexpected number of embeddings."
            );
        }

        List<float[]> result = new ArrayList<>();

        for (JsonNode embeddingNode : embeddingNodes) {
            JsonNode values = embeddingNode.get("values");

            if (values == null || !values.isArray()
                    || values.size() != properties.dimensions()) {
                throw new IllegalStateException(
                        "Gemini returned an embedding with invalid dimensions."
                );
            }

            float[] vector = new float[values.size()];

            for (int i = 0; i < values.size(); i++) {
                float value = values.get(i).floatValue();

                if (!Float.isFinite(value)) {
                    throw new IllegalStateException(
                            "Gemini returned a non-finite embedding value."
                    );
                }

                vector[i] = value;
            }

            result.add(vector);
        }

        return result;
    }
}
