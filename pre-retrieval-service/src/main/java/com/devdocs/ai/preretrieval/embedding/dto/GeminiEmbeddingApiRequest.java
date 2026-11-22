
package com.devdocs.ai.preretrieval.embedding.dto;

import java.util.List;

public record GeminiEmbeddingApiRequest(
        List<EmbeddingRequest> requests
) {

    public record EmbeddingRequest(
            String model,
            Content content,
            String taskType,
            Integer outputDimensionality
    ) {
    }

    public record Content(
            List<Part> parts
    ) {
    }

    public record Part(
            String text
    ) {
    }
}
