
package com.devdocs.ai.ingestdynamic.service;

import com.devdocs.ai.ingestdynamic.chunker.DynamicTextChunker;
import com.devdocs.ai.ingestdynamic.client.DocumentServiceClient;
import com.devdocs.ai.ingestdynamic.dto.CreateDocumentRequest;
import com.devdocs.ai.ingestdynamic.dto.CreateDocumentVersionRequest;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.dto.DocumentResponse;
import com.devdocs.ai.ingestdynamic.dto.DocumentVersionResponse;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import com.devdocs.ai.ingestdynamic.embedding.GeminiEmbeddingService;
import com.devdocs.ai.ingestdynamic.repository.DynamicChunkRepository;
import com.devdocs.ai.ingestdynamic.util.ChecksumUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DynamicIngestionService {

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
        List<CrawledPage> pages =
                websiteCrawler.crawl(websiteUrl, explicitVersion);

        if (pages.isEmpty()) {
            throw new IllegalStateException(
                    "Website crawler returned no pages."
            );
        }

        String versionLabel = pages.get(0).version();

        // 2. Calculate deterministic website snapshot checksum.
        String checksum =
                ChecksumUtils.websiteSnapshotChecksum(pages);

        // 3. Create or reuse the WEB document.
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

        // 4. Check whether this version already has the same content.
        List<DocumentVersionResponse> versions =
                documentServiceClient.findVersions(documentId);

        DocumentVersionResponse existingVersion = versions.stream()
                .filter(version ->
                        versionLabel.equals(version.versionLabel()))
                .findFirst()
                .orElse(null);

        if (existingVersion != null
                && checksum.equalsIgnoreCase(existingVersion.checksum())) {

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

        // 5. Chunk all crawled pages.
        List<TextChunk> chunks = new ArrayList<>();

        for (CrawledPage page : pages) {
            chunks.addAll(textChunker.chunk(page));
        }

        if (chunks.isEmpty()) {
            throw new IllegalStateException(
                    "Website ingestion produced no text chunks."
            );
        }

        // 6. Generate embeddings before modifying version/chunk data.
        List<float[]> embeddings =
                embeddingService.embed(chunks);

        // 7. Create or update version metadata.
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

        // 8. Replace chunks for this version.
        chunkRepository.replaceChunks(
                documentId,
                version.id(),
                chunks,
                embeddings
        );

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
