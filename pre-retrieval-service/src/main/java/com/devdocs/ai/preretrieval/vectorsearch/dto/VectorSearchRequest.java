package com.devdocs.ai.preretrieval.vectorsearch.dto;

import java.util.List;

public record VectorSearchRequest(
        Long versionId,
        List<Double> embedding,
        Integer topK
) {
}