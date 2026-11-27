
package com.devdocs.ai.preretrieval.query.service;

import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;
import com.devdocs.ai.preretrieval.contextstitching.service.ContextStitchingService;
import com.devdocs.ai.preretrieval.embedding.service.QueryEmbeddingService;
import com.devdocs.ai.preretrieval.filter.MetadataFilter;
import com.devdocs.ai.preretrieval.query.dto.QueryNormalizationResponse;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineRequest;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineResponse;
import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.reranking.service.RerankingService;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchRequest;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import com.devdocs.ai.preretrieval.vectorsearch.service.VectorSearchService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryPipelineService {

    private final QueryNormalizationService queryNormalizationService;
    private final QueryEmbeddingService queryEmbeddingService;
    private final VectorSearchService vectorSearchService;
    private final RerankingService rerankingService;
    private final ContextStitchingService contextStitchingService;

    public QueryPipelineService(
            QueryNormalizationService queryNormalizationService,
            QueryEmbeddingService queryEmbeddingService,
            VectorSearchService vectorSearchService,
            RerankingService rerankingService,
            ContextStitchingService contextStitchingService
    ) {
        this.queryNormalizationService = queryNormalizationService;
        this.queryEmbeddingService = queryEmbeddingService;
        this.vectorSearchService = vectorSearchService;
        this.rerankingService = rerankingService;
        this.contextStitchingService = contextStitchingService;
    }

    public QueryPipelineResponse retrieve(QueryPipelineRequest request) {
        return retrieve(request, null);
    }

    /**
     * @param filter validated URL metadata filter; null = standard
     *               version-scoped flow (versionId required)
     */
    public QueryPipelineResponse retrieve(QueryPipelineRequest request, MetadataFilter filter) {
        validateRequest(request, filter);

        QueryNormalizationResponse normalization =
                queryNormalizationService.normalize(request.query());

        List<Double> embedding =
                queryEmbeddingService.embed(normalization.normalizedQuery());

        // Story 4 Task 1: retrieve a wider top-N, rerank, keep top-k.
        int candidateCount = rerankingService.candidateCount(request.topK());

        VectorSearchRequest searchRequest = new VectorSearchRequest(
                request.versionId(),
                embedding,
                candidateCount,
                filter
        );

        List<VectorSearchResult> results =
                vectorSearchService.search(searchRequest);

        // The cross-encoder reads the user's own wording; synonym expansion
        // is only for the embedding/keyword stages.
        RerankingService.RerankResult rerank =
                rerankingService.rerankWithDetails(
                        normalization.originalQuery(),
                        results
                );

        List<RerankedChunk> rerankedResults = rerank.chunks().subList(
                0, Math.min(request.topK(), rerank.chunks().size()));

        List<StitchedContext> stitchedContexts =
                contextStitchingService.stitch(rerankedResults);

        return new QueryPipelineResponse(
                normalization.originalQuery(),
                normalization.normalizedQuery(),
                normalization.synonymTerms(),
                embedding.size(),
                request.versionId(),
                request.topK(),
                results,
                rerankedResults,
                stitchedContexts,
                filter == null ? null : new QueryPipelineResponse.AppliedFilter(
                        filter.mode().name(), filter.describe()),
                new QueryPipelineResponse.RerankingInfo(
                        rerank.strategy(), rerank.model(), results.size(),
                        rerankedResults.size(), rerank.latencyMs(), rerank.fallbackReason())
        );
    }

    private void validateRequest(QueryPipelineRequest request, MetadataFilter filter) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Query pipeline request must not be null."
            );
        }

        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException(
                    "Query must not be blank."
            );
        }

        // Without a filter, versionId is required (standard flow). With a
        // filter it is optional, but must be valid when supplied.
        if (filter == null
                ? request.versionId() == null || request.versionId() <= 0
                : request.versionId() != null && request.versionId() <= 0) {
            throw new IllegalArgumentException(
                    "versionId must be greater than zero."
            );
        }

        if (request.topK() == null
                || request.topK() < 1
                || request.topK() > 100) {
            throw new IllegalArgumentException(
                    "topK must be between 1 and 100."
            );
        }
    }
}
