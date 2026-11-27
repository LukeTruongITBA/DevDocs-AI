
package com.devdocs.ai.preretrieval.query.service;

import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;
import com.devdocs.ai.preretrieval.contextstitching.service.ContextStitchingService;
import com.devdocs.ai.preretrieval.embedding.service.QueryEmbeddingService;
import com.devdocs.ai.preretrieval.query.dto.QueryNormalizationResponse;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineRequest;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineResponse;
import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.reranking.service.RerankingService;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchRequest;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import com.devdocs.ai.preretrieval.vectorsearch.service.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryPipelineServiceTest {

    @Mock
    private QueryNormalizationService queryNormalizationService;

    @Mock
    private QueryEmbeddingService queryEmbeddingService;

    @Mock
    private VectorSearchService vectorSearchService;

    @Mock
    private RerankingService rerankingService;

    @Mock
    private ContextStitchingService contextStitchingService;

    @InjectMocks
    private QueryPipelineService queryPipelineService;

    private final String originalQuery =
            "How does Spring Boot auto-configuration work?";

    private final String normalizedQuery =
            "how does spring boot auto-configuration work?";

    private final Long versionId = 6L;

    private final Integer topK = 5;

    private List<Double> embedding;

    private QueryNormalizationResponse normalization;

    private VectorSearchResult vectorResult;

    private RerankedChunk rerankedChunk;

    private StitchedContext stitchedContext;

    @BeforeEach
    void setUp() {
        embedding = List.of(0.1, 0.2, 0.3);

        normalization = new QueryNormalizationResponse(
                originalQuery,
                normalizedQuery,
                List.of("auto configuration")
        );

        vectorResult = new VectorSearchResult(
                17L,
                versionId,
                5L,
                "Spring Boot",
                "4.2.0",
                "https://docs.spring.io/spring-boot/",
                0,
                "Spring Boot auto-configuration content",
                null,
                Map.of("source", "WEB"),
                0.85
        );

        rerankedChunk = new RerankedChunk(
                vectorResult,
                0.92,
                1
        );

        stitchedContext = new StitchedContext(
                5L,
                versionId,
                "Spring Boot",
                "4.2.0",
                "https://docs.spring.io/spring-boot/",
                0,
                0,
                "Spring Boot auto-configuration content",
                List.of()
        );
    }

    @Test
    void retrieveShouldExecuteCompletePipelineAndReturnResults() {
        QueryPipelineRequest request = new QueryPipelineRequest(
                originalQuery,
                versionId,
                topK
        );

        List<VectorSearchResult> searchResults =
                List.of(vectorResult);

        List<RerankedChunk> rerankedResults =
                List.of(rerankedChunk);

        List<StitchedContext> stitchedContexts =
                List.of(stitchedContext);

        when(queryNormalizationService.normalize(originalQuery))
                .thenReturn(normalization);

        when(queryEmbeddingService.embed(normalizedQuery))
                .thenReturn(embedding);

        when(vectorSearchService.search(any(VectorSearchRequest.class)))
                .thenReturn(searchResults);

        when(rerankingService.candidateCount(topK)).thenReturn(20);

        when(rerankingService.rerankWithDetails(originalQuery, searchResults))
                .thenReturn(new RerankingService.RerankResult(rerankedResults,
                        RerankingService.STRATEGY_CROSS_ENCODER, "test-model", 7, null));

        when(contextStitchingService.stitch(rerankedResults))
                .thenReturn(stitchedContexts);

        QueryPipelineResponse response =
                queryPipelineService.retrieve(request);

        assertNotNull(response);
        assertEquals(originalQuery, response.originalQuery());
        assertEquals(normalizedQuery, response.normalizedQuery());
        assertEquals(List.of("auto configuration"), response.synonymTerms());
        assertEquals(3, response.embeddingDimensions());
        assertEquals(versionId, response.versionId());
        assertEquals(topK, response.topK());

        assertEquals(searchResults, response.results());
        assertEquals(rerankedResults, response.rerankedResults());
        assertEquals(stitchedContexts, response.stitchedContexts());

        InOrder inOrder = inOrder(
                queryNormalizationService,
                queryEmbeddingService,
                vectorSearchService,
                rerankingService,
                contextStitchingService
        );

        inOrder.verify(queryNormalizationService)
                .normalize(originalQuery);

        inOrder.verify(queryEmbeddingService)
                .embed(normalizedQuery);

        org.mockito.ArgumentCaptor<VectorSearchRequest> searchCaptor =
                org.mockito.ArgumentCaptor.forClass(VectorSearchRequest.class);
        inOrder.verify(vectorSearchService)
                .search(searchCaptor.capture());
        assertEquals(20, searchCaptor.getValue().topK(), "retrieves the wider top-N");

        inOrder.verify(rerankingService)
                .rerankWithDetails(originalQuery, searchResults);

        assertEquals("cross-encoder", response.reranking().strategy());
        assertEquals(1, response.reranking().candidates());
        verify(rerankingService).candidateCount(topK);

        inOrder.verify(contextStitchingService)
                .stitch(rerankedResults);

        verifyNoMoreInteractions(
                queryNormalizationService,
                queryEmbeddingService,
                vectorSearchService,
                rerankingService,
                contextStitchingService
        );
    }

    @Test
    void retrieveShouldRejectNullRequest() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> queryPipelineService.retrieve(null)
        );

        assertEquals(
                "Query pipeline request must not be null.",
                exception.getMessage()
        );

        verifyNoInteractions(
                queryNormalizationService,
                queryEmbeddingService,
                vectorSearchService,
                rerankingService,
                contextStitchingService
        );
    }

    @Test
    void retrieveShouldRejectBlankQuery() {
        QueryPipelineRequest request =
                new QueryPipelineRequest("   ", versionId, topK);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> queryPipelineService.retrieve(request)
        );

        assertEquals("Query must not be blank.", exception.getMessage());

        verifyNoInteractions(
                queryNormalizationService,
                queryEmbeddingService,
                vectorSearchService,
                rerankingService,
                contextStitchingService
        );
    }

    @Test
    void retrieveShouldRejectNullVersionId() {
        QueryPipelineRequest request =
                new QueryPipelineRequest(originalQuery, null, topK);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> queryPipelineService.retrieve(request)
        );

        assertEquals(
                "versionId must be greater than zero.",
                exception.getMessage()
        );

        verifyNoInteractions(
                queryNormalizationService,
                queryEmbeddingService,
                vectorSearchService,
                rerankingService,
                contextStitchingService
        );
    }

    @Test
    void retrieveShouldRejectNonPositiveVersionId() {
        QueryPipelineRequest request =
                new QueryPipelineRequest(originalQuery, 0L, topK);

        assertThrows(
                IllegalArgumentException.class,
                () -> queryPipelineService.retrieve(request)
        );

        verifyNoInteractions(
                queryNormalizationService,
                queryEmbeddingService,
                vectorSearchService,
                rerankingService,
                contextStitchingService
        );
    }

    @Test
    void retrieveShouldRejectNullTopK() {
        QueryPipelineRequest request =
                new QueryPipelineRequest(originalQuery, versionId, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> queryPipelineService.retrieve(request)
        );

        verifyNoInteractions(
                queryNormalizationService,
                queryEmbeddingService,
                vectorSearchService,
                rerankingService,
                contextStitchingService
        );
    }

    @Test
    void retrieveShouldRejectTopKOutsideAllowedRange() {
        QueryPipelineRequest tooSmall =
                new QueryPipelineRequest(originalQuery, versionId, 0);

        QueryPipelineRequest tooLarge =
                new QueryPipelineRequest(originalQuery, versionId, 101);

        assertThrows(
                IllegalArgumentException.class,
                () -> queryPipelineService.retrieve(tooSmall)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> queryPipelineService.retrieve(tooLarge)
        );

        verifyNoInteractions(
                queryNormalizationService,
                queryEmbeddingService,
                vectorSearchService,
                rerankingService,
                contextStitchingService
        );
    }
}
