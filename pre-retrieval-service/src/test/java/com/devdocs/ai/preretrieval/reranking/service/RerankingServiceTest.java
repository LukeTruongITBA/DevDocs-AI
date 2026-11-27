package com.devdocs.ai.preretrieval.reranking.service;

import com.devdocs.ai.preretrieval.reranking.config.RerankingProperties;
import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RerankingServiceTest {

    private static final String MODEL = "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1@1427fd65";

    private CrossEncoderClient client;
    private RerankingService service;

    private final List<VectorSearchResult> candidates = List.of(
            result(1L, "about deployment", 0.90),
            result(2L, "about configuration", 0.80),
            result(3L, "about themes", 0.70)
    );

    @BeforeEach
    void setUp() {
        client = mock(CrossEncoderClient.class);
        service = new RerankingService(client, properties(true, 20));
    }

    @Test
    void reordersCandidatesByCrossEncoderScore() {
        when(client.score("how to theme", List.of("about deployment", "about configuration", "about themes")))
                .thenReturn(new double[]{0.10, 0.40, 0.95});

        RerankingService.RerankResult result = service.rerankWithDetails("how to theme", candidates);

        assertEquals(RerankingService.STRATEGY_CROSS_ENCODER, result.strategy());
        assertEquals(MODEL, result.model());
        assertNull(result.fallbackReason());
        assertEquals(List.of(3L, 2L, 1L), ids(result.chunks()));
        assertEquals(List.of(3, 2, 1), result.chunks().stream().map(RerankedChunk::originalRank).toList());
        assertEquals(0.95, result.chunks().getFirst().rerankScore());
    }

    @Test
    void tiesKeepVectorOrder() {
        when(client.score(anyString(), anyList())).thenReturn(new double[]{0.5, 0.5, 0.9});

        assertEquals(List.of(3L, 1L, 2L), ids(service.rerank("q", candidates)));
    }

    @Test
    void keepsOriginalOrderWhenRerankerFails() {
        when(client.score(anyString(), anyList()))
                .thenThrow(new CrossEncoderClient.RerankerUnavailableException("Reranker request failed: ResourceAccessException"));

        RerankingService.RerankResult result = service.rerankWithDetails("q", candidates);

        assertEquals(RerankingService.STRATEGY_VECTOR_ORDER, result.strategy());
        assertEquals(List.of(1L, 2L, 3L), ids(result.chunks()));
        assertEquals(0.90, result.chunks().getFirst().rerankScore());
        assertTrue(result.fallbackReason().contains("ResourceAccessException"));
        assertNull(result.model());
    }

    @Test
    void disabledRerankerKeepsOrderWithoutCallingModel() {
        RerankingService disabled = new RerankingService(client, properties(false, 20));

        RerankingService.RerankResult result = disabled.rerankWithDetails("q", candidates);

        assertEquals(List.of(1L, 2L, 3L), ids(result.chunks()));
        assertEquals("reranker disabled", result.fallbackReason());
        verifyNoInteractions(client);
        assertEquals(5, disabled.candidateCount(5));
    }

    @Test
    void candidateCountWidensToConfiguredTopNAndCaps() {
        assertEquals(20, service.candidateCount(5));
        assertEquals(30, service.candidateCount(30));
        assertEquals(100, new RerankingService(client, properties(true, 100)).candidateCount(5));
    }

    @Test
    void emptyCandidatesNeedNoModelCall() {
        assertEquals(List.of(), service.rerank("q", List.of()));
        verifyNoInteractions(client);
    }

    @Test
    void rejectsBlankQueryAndNullChunks() {
        assertThrows(IllegalArgumentException.class, () -> service.rerank(" ", candidates));
        assertThrows(IllegalArgumentException.class, () -> service.rerank("q", null));
    }

    private static RerankingProperties properties(boolean enabled, int candidates) {
        return new RerankingProperties(enabled, "http://localhost:8086", MODEL, candidates, 1000, 1000);
    }

    private static VectorSearchResult result(Long id, String content, double similarity) {
        return new VectorSearchResult(id, 9L, 5L, "Doc", "1", "src", id.intValue(), content,
                null, Map.of(), similarity);
    }

    private static List<Long> ids(List<RerankedChunk> chunks) {
        return chunks.stream().map(chunk -> chunk.chunk().id()).toList();
    }
}
