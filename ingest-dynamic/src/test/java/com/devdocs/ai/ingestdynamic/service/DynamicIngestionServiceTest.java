
package com.devdocs.ai.ingestdynamic.service;

import com.devdocs.ai.ingestdynamic.chunker.DynamicTextChunker;
import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.client.DocumentServiceClient;
import com.devdocs.ai.ingestdynamic.dto.CrawlFailure;
import com.devdocs.ai.ingestdynamic.dto.CrawlFailureStage;
import com.devdocs.ai.ingestdynamic.dto.CrawlResult;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.dto.DocumentResponse;
import com.devdocs.ai.ingestdynamic.dto.DocumentVersionResponse;
import com.devdocs.ai.ingestdynamic.dto.DynamicIngestionRequest;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import com.devdocs.ai.ingestdynamic.embedding.GeminiEmbeddingService;
import com.devdocs.ai.ingestdynamic.extractor.ExtractedPage;
import com.devdocs.ai.ingestdynamic.repository.DynamicChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicIngestionServiceTest {

    private static final String WEBSITE_URL =
            "https://docs.example.com/spring/";

    private static final String VERSION = "3.6";

    @Mock
    private WebsiteCrawler websiteCrawler;

    @Mock
    private DynamicTextChunker textChunker;

    @Mock
    private GeminiEmbeddingService embeddingService;

    @Mock
    private DocumentServiceClient documentServiceClient;

    @Mock
    private DynamicChunkRepository chunkRepository;

    private DynamicIngestionService ingestionService;

    private CrawledPage crawledPage;
    private TextChunk textChunk;
    private DocumentResponse document;

    @BeforeEach
    void setUp() {
        ingestionService = new DynamicIngestionService(
                websiteCrawler,
                textChunker,
                embeddingService,
                documentServiceClient,
                chunkRepository,
                new IngestionProperties(1000, 200, 100, 20,
                        4, 100_000, 200)
        );
        ExtractedPage extractedPage = new ExtractedPage(
                "Spring Boot Reference",
                WEBSITE_URL,
                "Spring Boot documentation test content."
        );

        crawledPage = new CrawledPage(
                extractedPage,
                VERSION
        );

        textChunk = new TextChunk(
                0,
                "Spring Boot documentation test content.",
                WEBSITE_URL,
                "Spring Boot Reference",
                VERSION
        );

        document = new DocumentResponse(
                10L,
                "Spring Boot Reference",
                "WEB",
                WEBSITE_URL,
                null,
                null
        );
    }

    @Test
    void shouldSkipIngestionWhenWebsiteContentIsUnchanged()
            throws Exception {

        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(completeCrawlResult());

        when(documentServiceClient.findDocumentBySourceUri(WEBSITE_URL))
                .thenReturn(document);

        String checksum = calculateChecksum();

        DocumentVersionResponse existingVersion =
                new DocumentVersionResponse(
                        20L,
                        10L,
                        VERSION,
                        checksum,
                        true,
                        null,
                        null
                );

        when(documentServiceClient.findVersions(10L))
                .thenReturn(List.of(existingVersion));
        when(textChunker.chunk(crawledPage))
                .thenReturn(List.of(textChunk));
        when(chunkRepository.compareStoredChunks(
                20L, List.of(textChunk)
        )).thenReturn(DynamicChunkRepository.StoredChunkState.COMPLETE);

        DynamicIngestionService.IngestionResult result =
                ingestionService.ingest(WEBSITE_URL, VERSION);

        assertTrue(result.skipped());
        assertEquals(10L, result.documentId());
        assertEquals(20L, result.versionId());
        assertEquals(0, result.chunksStored());

        verify(embeddingService, never()).embed(anyList());

        verify(chunkRepository, never())
                .insertChunks(anyLong(), anyLong(), anyList(), anyList());

        verify(documentServiceClient, never())
                .createVersion(anyLong(), any());
    }

    @Test
    void shouldFillOnlyMissingProviderEmbeddingsForUnchangedContent()
            throws Exception {

        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(completeCrawlResult());
        when(documentServiceClient.findDocumentBySourceUri(WEBSITE_URL))
                .thenReturn(document);
        DocumentVersionResponse existingVersion = new DocumentVersionResponse(
                20L, 10L, VERSION, calculateChecksum(), true, null, null);
        when(documentServiceClient.findVersions(10L))
                .thenReturn(List.of(existingVersion));
        when(textChunker.chunk(crawledPage)).thenReturn(List.of(textChunk));
        when(chunkRepository.compareStoredChunks(20L, List.of(textChunk)))
                .thenReturn(DynamicChunkRepository.StoredChunkState
                        .CONTENT_MATCHES_EMBEDDINGS_MISSING);
        when(chunkRepository.findMissingEmbeddingIndexes(20L))
                .thenReturn(List.of(0));
        List<float[]> vectors = List.of(new float[]{0.1f});
        when(embeddingService.embed(List.of(textChunk))).thenReturn(vectors);
        when(chunkRepository.fillMissingEmbeddings(20L, List.of(0), vectors))
                .thenReturn(1);

        DynamicIngestionService.IngestionResult result =
                ingestionService.ingest(WEBSITE_URL, VERSION);

        assertFalse(result.skipped());
        assertEquals(20L, result.versionId());
        assertEquals(1, result.chunksStored());
        verify(chunkRepository).fillMissingEmbeddings(20L, List.of(0), vectors);
        verify(chunkRepository, never())
                .insertChunks(anyLong(), anyLong(), anyList(), anyList());
        verify(documentServiceClient, never()).createDocument(any());
        verify(documentServiceClient, never()).createVersion(anyLong(), any());
    }

    @Test
    void shouldCreateEmbeddingsAndStoreChunksWhenContentChanges()
            throws Exception {

        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(completeCrawlResult());

        when(documentServiceClient.createDocument(any()))
                .thenReturn(document);

        when(textChunker.chunk(crawledPage))
                .thenReturn(List.of(textChunk));

        when(embeddingService.embed(anyList()))
                .thenReturn(List.of(new float[]{0.1f, 0.2f}));

        DocumentVersionResponse newVersion =
                new DocumentVersionResponse(
                        21L,
                        10L,
                        VERSION,
                        calculateChecksum(),
                        true,
                        null,
                        null
                );

        when(documentServiceClient.createVersion(
                eq(10L),
                any()
        )).thenReturn(newVersion);

        DynamicIngestionService.IngestionResult result =
                ingestionService.ingest(WEBSITE_URL, VERSION);

        assertFalse(result.skipped());
        assertEquals(10L, result.documentId());
        assertEquals(21L, result.versionId());
        assertEquals(1, result.pagesCrawled());
        assertEquals(1, result.chunksStored());

        verify(embeddingService).embed(anyList());

        verify(documentServiceClient)
                .createVersion(eq(10L), any());

        verify(chunkRepository).insertChunks(
                eq(10L),
                eq(21L),
                anyList(),
                anyList()
        );
    }

    @Test
    void shouldRejectConflictingExistingVersionWithoutReplacingChunks()
            throws Exception {
        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(completeCrawlResult());
        when(documentServiceClient.findDocumentBySourceUri(WEBSITE_URL))
                .thenReturn(document);
        when(documentServiceClient.findVersions(10L))
                .thenReturn(List.of(new DocumentVersionResponse(
                        21L, 10L, VERSION, "different-checksum", true,
                        null, null
                )));
        when(textChunker.chunk(crawledPage))
                .thenReturn(List.of(textChunk));
        assertThrows(
                com.devdocs.ai.ingestdynamic.exception.IngestionConflictException.class,
                () -> ingestionService.ingest(WEBSITE_URL, VERSION)
        );

        verify(embeddingService, never()).embed(anyList());
        verify(documentServiceClient, never()).createVersion(anyLong(), any());
        verify(chunkRepository, never()).insertChunks(
                anyLong(), anyLong(), anyList(), anyList()
        );
    }

    @Test
    void shouldRejectOversizedExtractedContentBeforeDocumentWrites()
            throws Exception {
        CrawledPage oversized = new CrawledPage(
                new ExtractedPage("large", WEBSITE_URL,
                        "x".repeat(100_001)),
                VERSION
        );
        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(new CrawlResult(
                        List.of(oversized), 1, 0, List.of(), 0, false
                ));

        assertThrows(IllegalArgumentException.class,
                () -> ingestionService.ingest(WEBSITE_URL, VERSION));
        verifyNoInteractions(documentServiceClient, embeddingService,
                chunkRepository);
    }

    @Test
    void shouldRejectBatchAboveConfiguredSourceLimitBeforeCrawling() {
        List<DynamicIngestionRequest> sources = List.of(
                new DynamicIngestionRequest(WEBSITE_URL, VERSION),
                new DynamicIngestionRequest(WEBSITE_URL, VERSION),
                new DynamicIngestionRequest(WEBSITE_URL, VERSION),
                new DynamicIngestionRequest(WEBSITE_URL, VERSION),
                new DynamicIngestionRequest(WEBSITE_URL, VERSION)
        );

        assertThrows(IllegalArgumentException.class,
                () -> ingestionService.ingestBatch(sources));
        verifyNoInteractions(websiteCrawler, documentServiceClient,
                embeddingService, chunkRepository);
    }

    @Test
    void shouldRejectChunkCountAboveConfiguredLimitBeforeDocumentWrites()
            throws Exception {
        ingestionService = new DynamicIngestionService(
                websiteCrawler, textChunker, embeddingService,
                documentServiceClient, chunkRepository,
                new IngestionProperties(1000, 200, 100, 20,
                        4, 100_000, 1)
        );
        TextChunk secondChunk = new TextChunk(
                1, "Second content", WEBSITE_URL, "Spring Boot Reference",
                VERSION
        );
        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(completeCrawlResult());
        when(textChunker.chunk(crawledPage))
                .thenReturn(List.of(textChunk, secondChunk));

        assertThrows(IllegalArgumentException.class,
                () -> ingestionService.ingest(WEBSITE_URL, VERSION));
        verifyNoInteractions(documentServiceClient, embeddingService,
                chunkRepository);
    }

    @Test
    void shouldFailWhenCrawlerReturnsNoPages()
            throws Exception {

        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(
                        new CrawlResult(
                                List.of(),
                                0,
                                0,
                                List.of(),
                                0,
                                false
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () -> ingestionService.ingest(
                        WEBSITE_URL,
                        VERSION
                )
        );

        verifyNoInteractions(
                documentServiceClient,
                textChunker,
                embeddingService,
                chunkRepository
        );
    }

    @Test
    void shouldRejectPartialCrawlWithoutPersistingTruncatedSource()
            throws Exception {

        CrawlFailure failure = new CrawlFailure(
                WEBSITE_URL + "configuration.html",
                CrawlFailureStage.FETCH,
                "HTTP 503 Service Unavailable."
        );

        CrawlResult incompleteResult = new CrawlResult(
                List.of(crawledPage),
                1,
                0,
                List.of(failure),
                0,
                false
        );

        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(incompleteResult);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ingestionService.ingest(WEBSITE_URL, VERSION)
        );
        assertTrue(exception.getMessage().contains("was incomplete"));
        assertTrue(exception.getMessage().contains("FETCH"));
        verifyNoInteractions(documentServiceClient, textChunker,
                embeddingService, chunkRepository);
    }

    @Test
    void shouldContinueBatchAfterOneSourceFails() throws Exception {
        String queryUrl = "https://docs.example.org/page?locale=en";
        String failedUrl = "https://files.example.org/unavailable.pdf";
        List<DynamicIngestionRequest> sources = List.of(
                new DynamicIngestionRequest(failedUrl, VERSION),
                new DynamicIngestionRequest(queryUrl, VERSION),
                new DynamicIngestionRequest(
                        "https://github.com/example/docs/tree/main/docs",
                        VERSION
                )
        );
        when(websiteCrawler.crawl(anyString(), any()))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    if (failedUrl.equals(url)) {
                        throw new IOException("HTTP 503");
                    }
                    return completeCrawlResult();
                });
        when(documentServiceClient.createDocument(any()))
                .thenReturn(document);
        when(textChunker.chunk(any(CrawledPage.class)))
                .thenReturn(List.of(textChunk));
        when(embeddingService.embed(anyList()))
                .thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(documentServiceClient.createVersion(eq(10L), any()))
                .thenReturn(new DocumentVersionResponse(
                        23L, 10L, VERSION, calculateChecksum(), true,
                        null, null
                ));

        DynamicIngestionService.BatchIngestionResult result =
                ingestionService.ingestBatch(sources);

        assertEquals(3, result.sourceCount());
        assertEquals(2, result.succeeded());
        assertEquals(1, result.failed());
        assertFalse(result.results().get(0).succeeded());
        assertEquals(failedUrl, result.results().get(0).sourceUrl());
        assertEquals("HTTP 503", result.results().get(0).error());
        assertTrue(result.results().get(1).succeeded());
        assertTrue(result.results().get(2).succeeded());
        verify(websiteCrawler).crawl(queryUrl, VERSION);
        verify(chunkRepository, times(2)).insertChunks(
                eq(10L), eq(23L), anyList(), anyList()
        );
    }

    @Test
    void shouldNotCreateVersionOrStoreChunksWhenEmbeddingFails()
            throws Exception {

        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(completeCrawlResult());

        when(textChunker.chunk(crawledPage))
                .thenReturn(List.of(textChunk));

        when(embeddingService.embed(anyList()))
                .thenThrow(new IllegalStateException(
                        "Gemini API unavailable."
                ));

        assertThrows(
                IllegalStateException.class,
                () -> ingestionService.ingest(
                        WEBSITE_URL,
                        VERSION
                )
        );

        verify(documentServiceClient, never())
                .createVersion(anyLong(), any());

        verify(chunkRepository, never())
                .insertChunks(
                        anyLong(),
                        anyLong(),
                        anyList(),
                        anyList()
                );
    }

    private CrawlResult completeCrawlResult() {
        return new CrawlResult(
                List.of(crawledPage),
                1,
                0,
                List.of(),
                0,
                false
        );
    }

    private String calculateChecksum() {
        return com.devdocs.ai.ingestdynamic.util.ChecksumUtils
                .websiteSnapshotChecksum(List.of(crawledPage));
    }
}
