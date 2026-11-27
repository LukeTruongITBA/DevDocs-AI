# Story 2 Task 1 publication manifest

This manifest identifies the files intended for a reviewed Story 2 Task 1
publication. It is preparatory only: no files have been staged, committed, or
pushed.

## Included source, configuration, tests, and documentation

- `.env.example`
- `README.md`
- `compose.yaml`
- `docs/story2-task1-implementation.md`
- `docs/story2-task1-publication-manifest.md`
- `document-service/src/main/java/com/devdocs/ai/document/DocumentController.java`
- `document-service/src/main/java/com/devdocs/ai/document/DocumentService.java`
- `document-service/src/test/java/com/devdocs/ai/document/DocumentControllerTest.java`
- `document-service/src/test/java/com/devdocs/ai/document/DocumentServiceTest.java`
- `ingest-dynamic/pom.xml`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/chunker/DynamicTextChunker.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/client/DocumentServiceClient.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/config/IngestionProperties.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/controller/DynamicIngestionController.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/dto/CrawlFailureStage.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/dto/CrawledPage.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/dto/DynamicIngestionBatchRequest.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/dto/TextChunk.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/exception/GlobalExceptionHandler.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/exception/IngestionConflictException.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/extractor/HtmlContentExtractor.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/fetcher/GitHubDocsFetcher.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/fetcher/WebsiteFetcher.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/repository/DynamicChunkRepository.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/service/DynamicIngestionService.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/service/WebsiteCrawler.java`
- `ingest-dynamic/src/main/java/com/devdocs/ai/ingestdynamic/util/UrlLogSanitizer.java`
- `ingest-dynamic/src/main/resources/application.yaml`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/chunker/DynamicTextChunkerTest.java`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/controller/DynamicIngestionControllerTest.java`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/dto/TextChunkTest.java`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/extractor/HtmlContentExtractorTest.java`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/fetcher/GitHubDocsFetcherTest.java`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/fetcher/WebsiteFetcherTest.java`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/repository/DynamicChunkRepositoryTest.java`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/service/DynamicIngestionServiceTest.java`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/service/WebsiteCrawlerTest.java`
- `ingest-dynamic/src/test/java/com/devdocs/ai/ingestdynamic/util/UrlLogSanitizerTest.java`

## Explicitly excluded

Existing backup files, crawler/extractor diagnostics, frontend snapshots,
cloudflare backups, generated build output, local PDF result files, and other
unrelated worktree artifacts are preserved locally and are not part of this
publication manifest.

## Proposed commit message

`feat(ingestion): complete Story 2 internet ingestion safely`
