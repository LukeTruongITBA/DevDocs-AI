
package com.devdocs.ai.ingestdynamic.service;

import com.devdocs.ai.ingestdynamic.chunker.DynamicTextChunker;
import com.devdocs.ai.ingestdynamic.client.DocumentServiceClient;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.dto.DocumentResponse;
import com.devdocs.ai.ingestdynamic.dto.DocumentVersionResponse;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import com.devdocs.ai.ingestdynamic.embedding.GeminiEmbeddingService;
import com.devdocs.ai.ingestdynamic.extractor.ExtractedPage;
import com.devdocs.ai.ingestdynamic.repository.DynamicChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private DynamicIngestionService ingestionService;

    private CrawledPage crawledPage;
    private TextChunk textChunk;
    private DocumentResponse document;

    @BeforeEach
    void setUp() {
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
                .thenReturn(List.of(crawledPage));

        when(documentServiceClient.createDocument(any()))
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

        DynamicIngestionService.IngestionResult result =
                ingestionService.ingest(WEBSITE_URL, VERSION);

        assertTrue(result.skipped());
        assertEquals(10L, result.documentId());
        assertEquals(20L, result.versionId());
        assertEquals(0, result.chunksStored());

        verify(embeddingService, never()).embed(anyList());
        verify(chunkRepository, never())
                .replaceChunks(anyLong(), anyLong(), anyList(), anyList());

        verify(documentServiceClient, never())
                .createVersion(anyLong(), any());
    }

    @Test
    void shouldCreateEmbeddingsAndStoreChunksWhenContentChanges()
            throws Exception {

        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(List.of(crawledPage));

        when(documentServiceClient.createDocument(any()))
                .thenReturn(document);

        when(documentServiceClient.findVersions(10L))
                .thenReturn(List.of());

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

        verify(chunkRepository).replaceChunks(
                eq(10L),
                eq(21L),
                anyList(),
                anyList()
        );
    }

    @Test
    void shouldFailWhenCrawlerReturnsNoPages()
            throws Exception {

        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> ingestionService.ingest(
                        WEBSITE_URL,
                        VERSION
                )
        );

        verify(documentServiceClient, never())
                .createDocument(any());

        verify(embeddingService, never()).embed(anyList());
    }

    @Test
    void shouldNotCreateVersionOrStoreChunksWhenEmbeddingFails()
            throws Exception {

        when(websiteCrawler.crawl(WEBSITE_URL, VERSION))
                .thenReturn(List.of(crawledPage));

        when(documentServiceClient.createDocument(any()))
                .thenReturn(document);

        when(documentServiceClient.findVersions(10L))
                .thenReturn(List.of());

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
                .replaceChunks(
                        anyLong(),
                        anyLong(),
                        anyList(),
                        anyList()
                );
    }

    private String calculateChecksum() {
        return com.devdocs.ai.ingestdynamic.util.ChecksumUtils
                .websiteSnapshotChecksum(List.of(crawledPage));
    }
}
