
package com.devdocs.ai.preretrieval.embedding.dto;

import java.util.List;

public record GeminiEmbeddingApiResponse(
        List<Embedding> embeddings
) {

    public record Embedding(
            List<Double> values
    ) {
    }
}
