package com.devdocs.ai.ingeststatic.dto;

import java.util.List;

public record GeminiEmbeddingApiResponse(
        List<Embedding> embeddings
) {

    public record Embedding(
            List<Double> values
    ) {
    }
}