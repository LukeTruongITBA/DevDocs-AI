package com.devdocs.ai.preretrieval.vectorsearch.dto;

import java.util.Map;

public record VectorSearchResult(
        Long id,
        Long versionId,
        Integer chunkIndex,
        String content,
        Integer pageNumber,
        Map<String, Object> metadata,
        Double similarity
) {
}