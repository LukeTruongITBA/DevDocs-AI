package com.devdocs.ai.preretrieval.query.dto;

import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;
import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;

import java.util.List;

/**
 * @param results         the wider vector top-N candidates, in similarity order
 * @param rerankedResults the final top-k after reranking (or vector order on fallback)
 * @param appliedFilter   the validated URL metadata filter, or null when the
 *                        standard flow ran without a filter
 * @param reranking       which reranking strategy ran, its model and latency
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
        AppliedFilter appliedFilter,
        RerankingInfo reranking
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
                topK, results, rerankedResults, stitchedContexts, null, null);
    }

    public record AppliedFilter(String mode, List<String> conditions) {
    }

    /**
     * @param strategy       {@code cross-encoder} or {@code vector-order} (fallback)
     * @param fallbackReason set only when vector order was kept
     */
    public record RerankingInfo(
            String strategy,
            String model,
            int candidates,
            int returned,
            long latencyMs,
            String fallbackReason
    ) {
    }
}
