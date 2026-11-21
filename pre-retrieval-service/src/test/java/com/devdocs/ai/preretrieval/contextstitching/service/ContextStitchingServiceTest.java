package com.devdocs.ai.preretrieval.contextstitching.service;

import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;
import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContextStitchingServiceTest {

    private ContextStitchingService stitchingService;

    @BeforeEach
    void setUp() {
        stitchingService = new ContextStitchingService();
    }

    @Test
    void shouldMergeAdjacentChunksFromSameDocumentAndVersion() {
        RerankedChunk chunk0 = createRerankedChunk(
                1L, 4L, 5L, 0, "First part.", 1
        );

        RerankedChunk chunk1 = createRerankedChunk(
                2L, 4L, 5L, 1, "Second part.", 2
        );

        List<StitchedContext> results =
                stitchingService.stitch(List.of(chunk0, chunk1));

        assertEquals(1, results.size());

        StitchedContext context = results.get(0);

        assertEquals(4L, context.documentId());
        assertEquals(5L, context.versionId());
        assertEquals(0, context.startChunkIndex());
        assertEquals(1, context.endChunkIndex());
        assertEquals("First part.\nSecond part.", context.content());
        assertEquals(2, context.citations().size());
    }

    @Test
    void shouldSortChunksByIndexBeforeStitching() {
        RerankedChunk chunk1 = createRerankedChunk(
                2L, 4L, 5L, 1, "Second part.", 1
        );

        RerankedChunk chunk0 = createRerankedChunk(
                1L, 4L, 5L, 0, "First part.", 2
        );

        List<StitchedContext> results =
                stitchingService.stitch(List.of(chunk1, chunk0));

        assertEquals(1, results.size());
        assertEquals(
                "First part.\nSecond part.",
                results.get(0).content()
        );
    }

    @Test
    void shouldNotMergeChunksWhenIndexesAreNotContiguous() {
        RerankedChunk chunk0 = createRerankedChunk(
                1L, 4L, 5L, 0, "First part.", 1
        );

        RerankedChunk chunk2 = createRerankedChunk(
                3L, 4L, 5L, 2, "Third part.", 2
        );

        List<StitchedContext> results =
                stitchingService.stitch(List.of(chunk0, chunk2));

        assertEquals(2, results.size());
    }

    @Test
    void shouldNotMergeChunksFromDifferentDocuments() {
        RerankedChunk chunk0 = createRerankedChunk(
                1L, 4L, 5L, 0, "Document A.", 1
        );

        RerankedChunk chunk1 = createRerankedChunk(
                2L, 6L, 5L, 1, "Document B.", 2
        );

        List<StitchedContext> results =
                stitchingService.stitch(List.of(chunk0, chunk1));

        assertEquals(2, results.size());
    }

    @Test
    void shouldNotMergeChunksFromDifferentVersions() {
        RerankedChunk chunk0 = createRerankedChunk(
                1L, 4L, 5L, 0, "Version 3.5.", 1
        );

        RerankedChunk chunk1 = createRerankedChunk(
                2L, 4L, 6L, 1, "Version 3.6.", 2
        );

        List<StitchedContext> results =
                stitchingService.stitch(List.of(chunk0, chunk1));

        assertEquals(2, results.size());
    }

    @Test
    void shouldPreserveCitationMetadataForEveryChunk() {
        RerankedChunk chunk0 = createRerankedChunk(
                1L, 4L, 5L, 0, "First part.", 1
        );

        RerankedChunk chunk1 = createRerankedChunk(
                2L, 4L, 5L, 1, "Second part.", 2
        );

        List<StitchedContext> results =
                stitchingService.stitch(List.of(chunk0, chunk1));

        StitchedContext context = results.get(0);

        assertEquals(2, context.citations().size());

        assertEquals(1L, context.citations().get(0).chunkId());
        assertEquals(0, context.citations().get(0).chunkIndex());
        assertEquals(1, context.citations().get(0).pageNumber());

        assertEquals(2L, context.citations().get(1).chunkId());
        assertEquals(1, context.citations().get(1).chunkIndex());
        assertEquals(2, context.citations().get(1).pageNumber());

        assertEquals(
                "https://docs.example.com/spring",
                context.citations().get(0).sourceUri()
        );

        assertEquals(
                "test",
                context.citations().get(0).metadata().get("source")
        );
    }

    @Test
    void shouldOrderStitchedContextsByBestOriginalRank() {
        RerankedChunk chunk0 = createRerankedChunk(
                1L, 4L, 5L, 0, "First part.", 5
        );

        RerankedChunk chunk1 = createRerankedChunk(
                2L, 4L, 5L, 1, "Second part.", 2
        );

        RerankedChunk separateChunk = createRerankedChunk(
                3L, 4L, 5L, 4, "Separate part.", 1
        );

        List<StitchedContext> results = stitchingService.stitch(
                List.of(chunk0, chunk1, separateChunk)
        );

        assertEquals(2, results.size());

        assertEquals(4, results.get(0).startChunkIndex());
        assertEquals(0, results.get(1).startChunkIndex());
    }

    @Test
    void shouldReturnEmptyListWhenInputIsEmpty() {
        List<StitchedContext> results =
                stitchingService.stitch(List.of());

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldRejectNullInputAndNullElements() {
        assertThrows(
                IllegalArgumentException.class,
                () -> stitchingService.stitch(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> stitchingService.stitch(
                        java.util.Arrays.asList((RerankedChunk) null)
                )
        );
    }

    @Test
    void shouldRejectChunksWithMissingDocumentVersionOrIndex() {
        RerankedChunk missingDocument = createRerankedChunk(
                1L, null, 5L, 0, "Content.", 1
        );

        RerankedChunk missingVersion = createRerankedChunk(
                2L, 4L, null, 0, "Content.", 1
        );

        RerankedChunk missingIndex = createRerankedChunk(
                3L, 4L, 5L, null, "Content.", 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> stitchingService.stitch(List.of(missingDocument))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> stitchingService.stitch(List.of(missingVersion))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> stitchingService.stitch(List.of(missingIndex))
        );
    }

    private RerankedChunk createRerankedChunk(
            Long chunkId,
            Long documentId,
            Long versionId,
            Integer chunkIndex,
            String content,
            Integer originalRank
    ) {
        VectorSearchResult chunk = new VectorSearchResult(
                chunkId,
                versionId,
                documentId,
                "Spring Boot Reference Documentation",
                "3.6",
                "https://docs.example.com/spring",
                chunkIndex,
                content,
                chunkIndex == null ? null : chunkIndex + 1,
                Map.of("source", "test"),
                0.90
        );

        return new RerankedChunk(
                chunk,
                0.90,
                originalRank
        );
    }
}