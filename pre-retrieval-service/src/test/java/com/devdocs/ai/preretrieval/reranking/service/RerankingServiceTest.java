package com.devdocs.ai.preretrieval.reranking.service;

import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RerankingServiceTest {

    private RerankingService rerankingService;

    @BeforeEach
    void setUp() {
        rerankingService = new RerankingService();
    }

    @Test
    void shouldRerankChunksUsingSimilarityAndKeywordOverlap() {
        VectorSearchResult chunk1 = createChunk(
                1L, 0, "Spring Boot simplifies Java development.", 0.90
        );

        VectorSearchResult chunk2 = createChunk(
                2L, 1, "Dependency injection manages object dependencies.", 0.80
        );

        List<RerankedChunk> results = rerankingService.rerank(
                "Spring Boot",
                List.of(chunk1, chunk2)
        );

        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).chunk().id());
        assertEquals(0.93, results.get(0).rerankScore(), 0.0001);
        assertEquals(1, results.get(0).originalRank());

        assertEquals(2L, results.get(1).chunk().id());
        assertEquals(0.56, results.get(1).rerankScore(), 0.0001);
        assertEquals(2, results.get(1).originalRank());
    }

    @Test
    void shouldPromoteChunkWithBetterKeywordOverlap() {
        VectorSearchResult chunk1 = createChunk(
                1L, 0, "Java framework documentation.", 0.90
        );

        VectorSearchResult chunk2 = createChunk(
                2L, 1, "Spring Boot Java framework documentation.", 0.80
        );

        List<RerankedChunk> results = rerankingService.rerank(
                "Spring Boot",
                List.of(chunk1, chunk2)
        );

        assertEquals(2L, results.get(0).chunk().id());
        assertEquals(0.86, results.get(0).rerankScore(), 0.0001);
        assertEquals(2, results.get(0).originalRank());
    }

    @Test
    void shouldPreserveOriginalRankAfterSorting() {
        VectorSearchResult chunk1 = createChunk(
                1L, 0, "Java documentation.", 0.70
        );

        VectorSearchResult chunk2 = createChunk(
                2L, 1, "Spring Boot documentation.", 0.90
        );

        List<RerankedChunk> results = rerankingService.rerank(
                "Spring Boot",
                List.of(chunk1, chunk2)
        );

        assertEquals(2, results.get(0).originalRank());
        assertEquals(1, results.get(1).originalRank());
    }

    @Test
    void shouldRejectNullQuery() {
        assertThrows(
                IllegalArgumentException.class,
                () -> rerankingService.rerank(null, List.of())
        );
    }

    @Test
    void shouldRejectBlankQuery() {
        assertThrows(
                IllegalArgumentException.class,
                () -> rerankingService.rerank("   ", List.of())
        );
    }

    @Test
    void shouldRejectNullChunks() {
        assertThrows(
                IllegalArgumentException.class,
                () -> rerankingService.rerank("Spring Boot", null)
        );
    }

    @Test
    void shouldRejectNonFiniteSimilarity() {
        VectorSearchResult chunk = createChunk(
                1L, 0, "Spring Boot documentation.", Double.NaN
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> rerankingService.rerank("Spring Boot", List.of(chunk))
        );
    }

    @Test
    void shouldReturnEmptyListWhenChunksAreEmpty() {
        List<RerankedChunk> results = rerankingService.rerank(
                "Spring Boot",
                List.of()
        );

        assertTrue(results.isEmpty());
    }

    private VectorSearchResult createChunk(
            Long id,
            Integer chunkIndex,
            String content,
            Double similarity
    ) {
        return new VectorSearchResult(
                id,
                5L,
                4L,
                "Spring Boot Reference Documentation",
                "3.6",
                "https://docs.spring.io/spring-boot/",
                chunkIndex,
                content,
                1,
                Map.of("source", "test"),
                similarity
        );
    }
}
