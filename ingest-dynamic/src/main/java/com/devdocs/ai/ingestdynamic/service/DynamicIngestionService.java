
package com.devdocs.ai.ingestdynamic.service;

import com.devdocs.ai.ingestdynamic.chunker.DynamicTextChunker;
import com.devdocs.ai.ingestdynamic.client.DocumentServiceClient;
import com.devdocs.ai.ingestdynamic.dto.CreateDocumentRequest;
import com.devdocs.ai.ingestdynamic.dto.CreateDocumentVersionRequest;
import com.devdocs.ai.ingestdynamic.dto.CrawlFailure;
import com.devdocs.ai.ingestdynamic.dto.CrawlResult;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.dto.DocumentResponse;
import com.devdocs.ai.ingestdynamic.dto.DocumentVersionResponse;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import com.devdocs.ai.ingestdynamic.embedding.GeminiEmbeddingService;
import com.devdocs.ai.ingestdynamic.repository.DynamicChunkRepository;
import com.devdocs.ai.ingestdynamic.util.ChecksumUtils;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DynamicIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DynamicIngestionService.class);

    private final WebsiteCrawler websiteCrawler;
    private final DynamicTextChunker textChunker;
    private final GeminiEmbeddingService embeddingService;
    private final DocumentServiceClient documentServiceClient;
    private final DynamicChunkRepository chunkRepository;

    public DynamicIngestionService(
            WebsiteCrawler websiteCrawler,
            DynamicTextChunker textChunker,
            GeminiEmbeddingService embeddingService,
            DocumentServiceClient documentServiceClient,
            DynamicChunkRepository chunkRepository
    ) {
        this.websiteCrawler = websiteCrawler;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.documentServiceClient = documentServiceClient;
        this.chunkRepository = chunkRepository;
    }

    public IngestionResult ingest(
            String websiteUrl,
            String explicitVersion
    ) throws IOException {

        // 1. Crawl website and detect its version.
        CrawlResult crawlResult =
                websiteCrawler.crawl(websiteUrl, explicitVersion);

        // 2. Reject incomplete crawls before modifying any data.
        if (!crawlResult.complete()) {
            log.warn("Rejected web ingestion because the crawl was incomplete: fetchedPages={}, retainedPages={}, failedPages={}, pendingPages={}",
                    crawlResult.fetchedPages(), crawlResult.pages().size(),
                    crawlResult.failures().size(), crawlResult.pendingPages());
            String failureDetails = crawlResult.failures()
                    .stream()
                    .limit(3)
                    .map(this::formatFailure)
                    .reduce(
                            (first, second) -> first + "; " + second
                    )
                    .orElse("No individual page failures recorded.");

            throw new IllegalStateException(
                    "Website crawl is incomplete. "
                            + "Fetched pages: " + crawlResult.fetchedPages()
                            + ", retained pages: " + crawlResult.pages().size()
                            + ", failed pages: " + crawlResult.failures().size()
                            + ", pending pages: " + crawlResult.pendingPages()
                            + ", maximum pages reached: "
                            + crawlResult.maxPagesReached()
                            + ". Details: " + failureDetails
            );
        }

        List<CrawledPage> pages = crawlResult.pages();

        // 3. Read the detected website version.
        String versionLabel = pages.get(0).version();

        // 4. Calculate deterministic website snapshot checksum.
        String checksum =
                ChecksumUtils.websiteSnapshotChecksum(pages);

        // 5. Create or reuse the WEB document.
        String normalizedUrl = pages.get(0).page().sourceUrl();

        DocumentResponse document =
                documentServiceClient.createDocument(
                        new CreateDocumentRequest(
                                pages.get(0).page().title(),
                                "WEB",
                                normalizedUrl
                        )
                );

        if (document == null || document.id() == null) {
            throw new IllegalStateException(
                    "Document Service returned an invalid document."
            );
        }

        Long documentId = document.id();

        // 6. Check whether this version already has the same content.
        List<DocumentVersionResponse> versions =
                documentServiceClient.findVersions(documentId);

        DocumentVersionResponse existingVersion = versions.stream()
                .filter(version ->
                        versionLabel.equals(version.versionLabel()))
                .findFirst()
                .orElse(null);

        if (existingVersion != null
                && checksum.equalsIgnoreCase(existingVersion.checksum())) {

            log.info("Skipped unchanged web ingestion: documentId={}, versionId={}, pages={}",
                    documentId, existingVersion.id(), pages.size());

            return new IngestionResult(
                    documentId,
                    existingVersion.id(),
                    versionLabel,
                    checksum,
                    pages.size(),
                    0,
                    true
            );
        }

        // 7. Chunk all crawled pages.
        List<TextChunk> chunks = new ArrayList<>();

        for (CrawledPage page : pages) {
            chunks.addAll(textChunker.chunk(page));
        }

        if (chunks.isEmpty()) {
            throw new IllegalStateException(
                    "Website ingestion produced no text chunks."
            );
        }

        // 8. Generate embeddings before modifying version/chunk data.
        List<float[]> embeddings =
                embeddingService.embed(chunks);

        // 9. Create or update version metadata.
        DocumentVersionResponse version =
                documentServiceClient.createVersion(
                        documentId,
                        new CreateDocumentVersionRequest(
                                versionLabel,
                                checksum
                        )
                );

        if (version == null || version.id() == null) {
            throw new IllegalStateException(
                    "Document Service returned an invalid version."
            );
        }

        // 10. Replace chunks for this version.
        chunkRepository.replaceChunks(
                documentId,
                version.id(),
                chunks,
                embeddings
        );

        log.info("Persisted web ingestion: documentId={}, versionId={}, pageCount={}, chunkCount={}",
                documentId, version.id(), pages.size(), chunks.size());

        return new IngestionResult(
                documentId,
                version.id(),
                versionLabel,
                checksum,
                pages.size(),
                chunks.size(),
                false
        );
    }

    private String formatFailure(CrawlFailure failure) {
        return failure.stage()
                + " at "
                + failure.url()
                + ": "
                + failure.message();
    }

    public record IngestionResult(
            Long documentId,
            Long versionId,
            String versionLabel,
            String checksum,
            int pagesCrawled,
            int chunksStored,
            boolean skipped
    ) {
    }
}
