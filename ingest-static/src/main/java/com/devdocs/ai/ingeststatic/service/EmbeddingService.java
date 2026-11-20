package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.ChunkEmbedding;
import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.config.EmbeddingProperties;
import com.devdocs.ai.ingeststatic.dto.GeminiEmbeddingApiRequest;
import com.devdocs.ai.ingeststatic.dto.GeminiEmbeddingApiResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private static final String TASK_TYPE = "RETRIEVAL_DOCUMENT";

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    public EmbeddingService(
            RestClient.Builder restClientBuilder,
            EmbeddingProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public List<ChunkEmbedding> embed(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chunks must not be empty."
            );
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