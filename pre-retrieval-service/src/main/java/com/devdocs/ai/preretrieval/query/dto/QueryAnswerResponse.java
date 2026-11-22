package com.devdocs.ai.preretrieval.query.dto;

import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;

import java.util.List;

public record QueryAnswerResponse(
        String originalQuery,
        String normalizedQuery,
        String answer,
        Long versionId,
        Integer topK,
        List<StitchedContext> citations
) {
}