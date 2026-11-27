package com.devdocs.ai.preretrieval.query.dto;

import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;
import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;

import java.util.List;

/**
 * @param appliedFilter the validated URL metadata filter, or null when the
 *                      standard flow ran without a filter
 */
public record QueryPipelineResponse(
        String originalQuery,
        String normalizedQuery,
        List<String> synonymTerms,
        int embeddingDimensions,
        Long versionId,
        Integer topK,
        List<VectorSearchResult> results,
        List<RerankedChunk> rerankedResults,
        List<StitchedContext> stitchedContexts,
        AppliedFilter appliedFilter
) {
    public QueryPipelineResponse(
            String originalQuery,
            String normalizedQuery,
            List<String> synonymTerms,
            int embeddingDimensions,
            Long versionId,
            Integer topK,
            List<VectorSearchResult> results,
            List<RerankedChunk> rerankedResults,
            List<StitchedContext> stitchedContexts
    ) {
        this(originalQuery, normalizedQuery, synonymTerms, embeddingDimensions, versionId,
                topK, results, rerankedResults, stitchedContexts, null);
    }

    public record AppliedFilter(String mode, List<String> conditions) {
    }
}
