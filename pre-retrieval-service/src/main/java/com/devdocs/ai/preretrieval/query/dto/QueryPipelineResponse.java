
package com.devdocs.ai.preretrieval.query.dto;

import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;
import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;

import java.util.List;

public record QueryPipelineResponse(
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
}
