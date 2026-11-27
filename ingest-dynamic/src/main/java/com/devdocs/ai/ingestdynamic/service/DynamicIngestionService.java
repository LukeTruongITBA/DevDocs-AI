
package com.devdocs.ai.ingestdynamic.service;

import com.devdocs.ai.ingestdynamic.chunker.DynamicTextChunker;
import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.client.DocumentServiceClient;
import com.devdocs.ai.ingestdynamic.dto.CreateDocumentRequest;
import com.devdocs.ai.ingestdynamic.dto.CreateDocumentVersionRequest;
import com.devdocs.ai.ingestdynamic.dto.CrawlFailure;
import com.devdocs.ai.ingestdynamic.dto.CrawlResult;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.dto.DynamicIngestionRequest;
import com.devdocs.ai.ingestdynamic.dto.DocumentResponse;
import com.devdocs.ai.ingestdynamic.dto.DocumentVersionResponse;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import com.devdocs.ai.ingestdynamic.embedding.EmbeddingService;
import com.devdocs.ai.ingestdynamic.exception.IngestionConflictException;
import com.devdocs.ai.ingestdynamic.repository.DynamicChunkRepository;
import com.devdocs.ai.ingestdynamic.util.ChecksumUtils;
import com.devdocs.ai.ingestdynamic.util.UrlLogSanitizer;
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
    private final EmbeddingService embeddingService;
    private final DocumentServiceClient documentServiceClient;
    private final DynamicChunkRepository chunkRepository;
    private final IngestionProperties properties;

    public DynamicIngestionService(
            WebsiteCrawler websiteCrawler,
            DynamicTextChunker textChunker,
            EmbeddingService embeddingService,
            DocumentServiceClient documentServiceClient,
            DynamicChunkRepository chunkRepository,
            IngestionProperties properties
    ) {
        this.websiteCrawler = websiteCrawler;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.documentServiceClient = documentServiceClient;
        this.chunkRepository = chunkRepository;
        this.properties = properties;
    }

    public BatchIngestionResult ingestBatch(
            List<DynamicIngestionRequest> sources
    ) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one dynamic source is required."
            );
        }

        if (sources.size() > properties.maxBatchSources()) {
            throw new IllegalArgumentException(
                    "Dynamic ingestion batch exceeds the configured source limit of "
                            + properties.maxBatchSources() + "."
            );
        }

        log.info("Starting dynamic ingestion batch: sourceCount={}",
                sources.size());
        List<SourceIngestionResult> results = new ArrayList<>();

        for (DynamicIngestionRequest source : sources) {
            String sourceUrl = source.websiteUrl();
            try {
                IngestionResult result = ingest(
                        sourceUrl,
                        source.explicitVersion()
                );
                results.add(new SourceIngestionResult(
                        sourceUrl, true, result, null
                ));
            } catch (Exception exception) {
                String message = safeFailureMessage(exception);
                log.warn(
                        "Dynamic ingestion batch source failed: url={}, reason={}",
                        UrlLogSanitizer.sanitize(sourceUrl),
                        message
                );
                results.add(new SourceIngestionResult(
                        sourceUrl, false, null, message
                ));
            }
        }

        long succeeded = results.stream()
                .filter(SourceIngestionResult::succeeded)
                .count();
        int failed = results.size() - (int) succeeded;
        log.info(
                "Dynamic ingestion batch completed: sourceCount={}, "
                        + "succeeded={}, failed={}",
                results.size(), succeeded, failed
        );
        return new BatchIngestionResult(
                results.size(), (int) succeeded, failed, results
        );
    }

    private String safeFailureMessage(Exception exception) {
        if (exception instanceof org.springframework.web.client.RestClientResponseException response) {
            return "External service returned HTTP "
                    + response.getStatusCode().value() + ".";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Source failed with "
                    + exception.getClass().getSimpleName() + ".";
        }
        return message.length() > 400
                ? message.substring(0, 400)
                : message;
    }

    public IngestionResult ingest(
            String websiteUrl,
            String explicitVersion
    ) throws IOException {

        // 1. Crawl website and detect its version.
        CrawlResult crawlResult =
                websiteCrawler.crawl(websiteUrl, explicitVersion);

        // A source is only successful when its full configured scope was
        // fetched. Batch isolation is handled by ingestBatch around this call.
        if (!crawlResult.complete()) {
            log.warn(
                    "Rejecting incomplete dynamic source: fetchedPages={}, "
                            + "retainedPages={}, failedPages={}, "
                            + "pendingPages={}, maxPagesReached={}",
                    crawlResult.fetchedPages(), crawlResult.pages().size(),
                    crawlResult.failures().size(), crawlResult.pendingPages(),
                    crawlResult.maxPagesReached()
            );
            String failureDetails = crawlResult.failures()
                    .stream()
                    .limit(3)
                    .map(this::formatFailure)
                    .reduce(
                            (first, second) -> first + "; " + second
                    )
                    .orElse("No individual page failures recorded.");

            throw new IllegalStateException(
                    "Dynamic source was incomplete and was not persisted. "
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

        long extractedCharacters = pages.stream()
                .mapToLong(page -> page.page().content().length())
                .sum();
        if (extractedCharacters > properties.maxExtractedCharacters()) {
            throw new IllegalArgumentException(
                    "Extracted content exceeds the configured character limit of "
                            + properties.maxExtractedCharacters() + "."
            );
        }

        List<TextChunk> chunks = new ArrayList<>();
        for (CrawledPage page : pages) {
            chunks.addAll(textChunker.chunk(page));
        }
        if (chunks.isEmpty()) {
            throw new IllegalStateException(
                    "Website ingestion produced no text chunks."
            );
        }
        if (chunks.size() > properties.maxChunksPerSource()) {
            throw new IllegalArgumentException(
                    "Ingestion exceeds the configured chunk limit of "
                            + properties.maxChunksPerSource() + "."
            );
        }

        // 3. Read the detected website version.
        String versionLabel = pages.get(0).version();

        // 4. Calculate deterministic website snapshot checksum.
        String checksum =
                ChecksumUtils.websiteSnapshotChecksum(pages);

        // 5. Read the document without invoking the document-service upsert.
        String normalizedUrl = pages.get(0).page().sourceUrl();

        DocumentResponse document =
                documentServiceClient.findDocumentBySourceUri(normalizedUrl);
        Long documentId = document == null ? null : document.id();

        // 6. Check whether this version already has the same content.
        List<DocumentVersionResponse> versions = documentId == null
                ? List.of()
                : documentServiceClient.findVersions(documentId);

        DocumentVersionResponse existingVersion = versions.stream()
                .filter(version ->
                        versionLabel.equals(version.versionLabel()))
                .findFirst()
                .orElse(null);

        DynamicChunkRepository.StoredChunkState storedState =
                existingVersion != null
                        && checksum.equalsIgnoreCase(existingVersion.checksum())
                        ? chunkRepository.compareStoredChunks(
                                existingVersion.id(), chunks)
                        : DynamicChunkRepository.StoredChunkState.MISMATCH;

        if (storedState == DynamicChunkRepository.StoredChunkState.COMPLETE) {

            log.info("Skipped unchanged web ingestion: documentId={}, versionId={}, pages={}",
                    documentId, existingVersion.id(), pages.size());

            return new IngestionResult(
                    documentId,
                    existingVersion.id(),
                    versionLabel,
                    checksum,
                    pages.size(),
                    0,
                    true,
                    crawlResult.failures()
            );
        }

        if (storedState == DynamicChunkRepository.StoredChunkState
                .CONTENT_MATCHES_EMBEDDINGS_MISSING) {
            // Same version, same snapshot checksum, same chunk content: only
            // the configured provider's vectors are missing (e.g. a chunk
            // first stored with Gemini, now served by Ollama). Fill those
            // NULL vectors; content, metadata and other columns stay as-is.
            List<Integer> missing = chunkRepository
                    .findMissingEmbeddingIndexes(existingVersion.id());
            List<TextChunk> toEmbed = missing.stream()
                    .map(chunks::get)
                    .toList();
            int filled = toEmbed.isEmpty() ? 0 : chunkRepository.fillMissingEmbeddings(
                    existingVersion.id(), missing, embeddingService.embed(toEmbed));

            log.info("Filled missing embeddings for unchanged web source: "
                            + "documentId={}, versionId={}, filledChunks={}",
                    documentId, existingVersion.id(), filled);

            return new IngestionResult(
                    documentId,
                    existingVersion.id(),
                    versionLabel,
                    checksum,
                    pages.size(),
                    filled,
                    false,
                    crawlResult.failures()
            );
        }

        if (document != null) {
            log.warn(
                    "Rejecting conflicting web ingestion without modifying "
                            + "existing data: documentId={}, versionId={}, "
                            + "version={}, source={}",
                    documentId,
                    existingVersion == null ? null : existingVersion.id(),
                    versionLabel,
                    UrlLogSanitizer.sanitize(normalizedUrl)
            );
            throw new IngestionConflictException(
                    "An existing source conflicts with this snapshot; "
                            + "no existing records were modified."
            );
        }

        // 8. Generate embeddings before creating any new database records.
        List<float[]> embeddings =
                embeddingService.embed(chunks);

        // 9. Create a new WEB document only after extraction and embedding
        // have succeeded. A concurrent source collision fails safely at the
        // document service's unique constraint instead of replacing data.
        if (document == null) {
            document = documentServiceClient.createDocument(
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

            documentId = document.id();
        }

        // 10. Create version metadata. Existing sources were rejected above.
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

        // 11. Insert chunks for this newly-created version.
        chunkRepository.insertChunks(
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
                false,
                crawlResult.failures()
        );
    }

    private String formatFailure(CrawlFailure failure) {
        return failure.stage()
                + " at "
                + UrlLogSanitizer.sanitize(failure.url())
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
            boolean skipped,
            List<CrawlFailure> failures
    ) {
        public IngestionResult(
                Long documentId,
                Long versionId,
                String versionLabel,
                String checksum,
                int pagesCrawled,
                int chunksStored,
                boolean skipped
        ) {
            this(documentId, versionId, versionLabel, checksum,
                    pagesCrawled, chunksStored, skipped, List.of());
        }

        public IngestionResult {
            failures = List.copyOf(failures);
        }
    }

    public record SourceIngestionResult(
            String sourceUrl,
            boolean succeeded,
            IngestionResult ingestion,
            String error
    ) {
    }

    public record BatchIngestionResult(
            int sourceCount,
            int succeeded,
            int failed,
            List<SourceIngestionResult> results
    ) {
        public BatchIngestionResult {
            results = List.copyOf(results);
        }
    }
}
