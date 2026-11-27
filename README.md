# DevDocs AI

Version-aware retrieval-augmented assistant for Java and Spring developers.

## PDF and web ingestion

PDF ingestion is handled by `ingest-static`: the upload endpoint validates the PDF, extracts each page with PDFBox, normalizes line-end hyphenation, removes repeated running headers/standalone page numbers, formats positioned cells, chunks the page text, creates Gemini document embeddings, and persists chunks and metadata to PostgreSQL with pgvector. Chunks remain associated with their one-based source page. Web ingestion is handled by `ingest-dynamic` and uses the same embedding model/dimension contract.

PDF layout reconstruction uses text positions. Repeated large gutters are treated as two-column text flow and read down the left column before the right; multiple aligned cell gaps are kept row-wise and separated with ` | `. This is deterministic heuristic layout handling, not OCR or a full table understanding engine. Image-only PDFs, complex nested tables, rotated text, and ambiguous page layouts can still require review.

## Metadata contract

Every newly persisted chunk must include `doc_id`, `version`, `source`, `page`, `chunk_index`, and `checksum`. `source_type` is also stored to distinguish the page representation:

- Static PDF chunks use a positive, one-based integer for `page`, matching the database `page_number` column.
- Web chunks use an object such as `{ "type": "web", "url": "https://docs.example/page", "title": "Page title" }`. The database `page_number` remains SQL `NULL` for web content.
- Remote PDFs ingested by `ingest-dynamic` use the existing dynamic `source_type=WEB` schema required by migration V3. Their `page` object retains the original one-based PDF page in `pdf_page_number` (for example, `{"type":"web","url":"https://files.example/guide.pdf","title":"guide.pdf","content_type":"application/pdf","pdf_page_number":2}`). SQL `page_number` remains `NULL` for compatibility with that constraint.
- `checksum` is the lower-case SHA-256 checksum of the exact chunk text. `chunk_index` is zero-based within the document version.

Migration `V3__enforce_chunk_metadata_contract.sql` backfills existing rows from their authoritative document/version/chunk columns, then adds a validated check constraint. For web rows, the backfill uses the stored source URL and page title to create the web `page` object. This repository change does not alter a database. Do not apply the migration to a shared database without approval and a backup/rollback plan.

## Configuration

The compose file reads `.env` from the project root. Copy `.env.example` to `.env` locally and fill secrets only in the ignored local file. Never commit or print its contents.

| Variable | Default | Purpose |
|---|---|---|
| `CHUNK_SIZE` | `1000` | Maximum target chunk size in characters. |
| `CHUNK_OVERLAP` | `200` | Character overlap between chunks; must be smaller than chunk size. |
| `OLLAMA_EMBEDDING_URL` | `http://host.docker.internal:11434/api/embed` | Ollama embedding endpoint (compose). Outside compose the services default to `http://localhost:11434/api/embed`. |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text:137m-v1.5-fp16` | Embedding model (768 dimensions, stored in `chunks.embedding_ollama`). |
| `OLLAMA_EMBEDDING_BATCH_SIZE` | `16` | Chunks per embedding request. |
| `EMBEDDING_PROVIDER` | `ollama` | `ollama` (768-dim column) or `gemini` (legacy 1536-dim column). Each provider validates its own dimension count. |
| `EMBEDDING_API_URL`, `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS`, `GEMINI_API_KEY` | unset | Legacy Gemini path, used only outside compose with `EMBEDDING_PROVIDER=gemini` and 1536 dimensions. `GEMINI_API_KEY` is a secret. |
| `EMBEDDING_MAX_ATTEMPTS` | `1` | Gemini retry attempts per batch (retries off by default). |
| `EMBEDDING_INITIAL_BACKOFF_MILLIS` / `EMBEDDING_MAX_BACKOFF_MILLIS` / `EMBEDDING_JITTER_RATIO` | `250` / `2000` / `0.2` | Bounded Gemini retry backoff, used only when retries are enabled. |
| `RERANKER_URL` | `http://reranker:80` (compose) | Cross-encoder (TEI) endpoint; see [docs/story4-task1-reranker.md](docs/story4-task1-reranker.md). |
| `RERANKER_CANDIDATE_COUNT` | `20` | Wider top-N retrieved before reranking (max 32). `RERANKER_ENABLED=false` keeps vector order. |
| `POSTGRES_DB` | `devdocs` | Local database name. |
| `POSTGRES_USER` | `devdocs` | Local database user. |
| `POSTGRES_PASSWORD` | unset in example | Set a local database password in `.env`. |

Embeddings use two independent vector columns. Ollama `nomic-embed-text` (768 dims) is written to `chunks.embedding_ollama VECTOR(768)`; this is the default, and `compose.yaml` uses it. Gemini `gemini-embedding-001` (1536 dims) stays in `chunks.embedding VECTOR(1536)`. Migration `V4__add_ollama_embedding_column.sql` adds the Ollama column additively; see [docs/ollama-embedding.md](docs/ollama-embedding.md). Each provider validates its own dimension count, and pre-retrieval routes queries to the matching column.

## Story status (2026-11-27)

| Story / task | Record |
|---|---|
| Story 1 Task 1, PDF ingestion (re-verified under Ollama) | [docs/story1-task1-manual-review.md](docs/story1-task1-manual-review.md) |
| Story 2 Task 1, Internet ingestion (production batch under Ollama) | [docs/story2-task1-implementation.md](docs/story2-task1-implementation.md) |
| Story 3 Task 1, Safe metadata filtering (with Postman collection) | [docs/story3-task1-metadata-filtering.md](docs/story3-task1-metadata-filtering.md) |
| Story 4 Task 1, Cross-encoder reranker (golden-set benchmark) | [docs/story4-task1-reranker.md](docs/story4-task1-reranker.md) |

The per-task acceptance and baseline checklist, review record and known deviations are in [docs/epic1-task1-status.md](docs/epic1-task1-status.md).

Running the full stack also starts the `reranker` service: the TEI CPU image plus a pinned cross-encoder, downloaded on first start, which uses about 3.6 GB RAM. Ollama must be running on the host with `nomic-embed-text:137m-v1.5-fp16` pulled.

## Running tests

From the repository root:

```powershell
.\mvnw.cmd -f ingest-static\pom.xml test
.\mvnw.cmd -f ingest-dynamic\pom.xml test
```

Focused static ingestion tests:

```powershell
.\mvnw.cmd -f ingest-static\pom.xml "-Dtest=PdfExtractionServiceTest,TextChunkerTest,ChunkPersistenceServiceTest,PdfIngestionControllerTest,StaticChunkMetadataFactoryTest,EmbeddingPropertiesTest" test
```

`PdfSampleExtractionReportTest` reads six PDFs from the user Desktop path encoded in that diagnostic test. It is optional and only runs when those external files are present. Unit tests use generated PDFs or mocks; they do not prove live database persistence or call production credentials.

## Running PDF ingestion

Start the stack with `docker compose up --build`, register a STATIC document and version through the document service using the PDF's SHA-256 checksum, then upload the exact same PDF bytes with the resulting IDs:

```powershell
curl.exe -X POST http://localhost:8080/api/ingestion/static/pdf `
  -F "file=@C:\path\to\document.pdf" `
  -F "documentId=<registered-document-id>" `
  -F "versionId=<registered-version-id>"
```

The API gateway must route the static-ingestion endpoint for this request; if it is not configured in the current gateway, call the service directly at port `8082`. Real ingestion writes PostgreSQL and calls Gemini. Obtain approval before running it against a shared/existing database or making billable API calls.

## Idempotency verification

The service checks the uploaded file checksum against the registered version. A repeat upload validates stored chunk indexes/checksums, repairs missing embeddings, inserts missing chunks after partial ingestion, and reports `alreadyIngested=true` when the version is complete. The unique `(version_id, chunk_index)` constraint and transaction-backed insert protect concurrent writes; a concurrent unique-key winner is re-read and validated. Verify idempotency by recording the version's chunk count/checksums before and after an identical upload and confirming they are unchanged. Do this only in an approved test database; do not delete or truncate existing data.

## Manual text-quality review

Use two distinct sample PDFs: one with actual tables and one with prose laid out in multiple columns. Do not count a multi-column table as the multi-column text-layout sample. Persist at least 20 PDF chunks through the production path, select 20 chunk IDs randomly from those rows, and compare each complete stored chunk with its exact source PDF page. Record selection method, document/version, chunk ID, page, reading-order observations, Unicode, hyphenation, word/sentence continuity, and table/column fidelity. Do not treat terminal display artifacts as source corruption; inspect UTF-8 content and the source PDF directly. Avoid including sensitive source text in the report.

## Story 1 – Task 1 verification record

On 2026-11-26, `compliance_responsibilities.pdf` (table sample) and the separate ACL Anthology paper [Text Segmentation of Digitized Clinical Texts](https://aclanthology.org/L16-1570.pdf) (two-column prose sample) were ingested through the production static endpoint. The persisted versions contain 5 and 39 chunks respectively. An identical upload retry for each version left the ordered chunk IDs, indices, and checksums unchanged. Database validation covered all 74 current chunks, including 11 web chunks: no required metadata fields were missing or inconsistent, and all stored embeddings were 1536-dimensional.

The reproducible 20-chunk manual review and per-chunk results are recorded in [the Story 1 Task 1 manual review](docs/story1-task1-manual-review.md). V3 was applied transactionally after a validated custom-format database backup. Publish the task changes, test results, and evidence to GitHub for professor review; no separate live demonstration is required unless requested, and no Product Owner acceptance is claimed here.

### Story 1 baseline automated test results

The following module test results were obtained on 2026-11-26:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| ingest-static | 38 | 0 | 0 | 0 |
| ingest-dynamic | 90 | 0 | 0 | 0 |
| Total | 128 | 0 | 0 | 0 |

The suites were run from the repository root with:

```powershell
.\mvnw.cmd -f ingest-static\pom.xml test
.\mvnw.cmd -f ingest-dynamic\pom.xml test
```

### Common Baseline and project review workflow

The professor's Common Baseline requirements remain:

1. "Code is peer-reviewed and merged to the main branch."
2. "Automated tests pass, with at least one test for the success path and one for a failure path."
3. "No secrets in the repository, and configuration is documented."
4. "Errors and key events are logged."
5. "The README or docs are updated, and the result is demonstrated to the product owner."

For this project, GPT/Codex performs code review and verification during AI execution; a separate human peer reviewer is not required. The professor monitors progress and deliverables through GitHub, so task code, documentation, test results, and evidence must be published there. GitHub publication makes the result available for Product Owner/professor review. No Product Owner acceptance is claimed here; record acceptance only when there is evidence of it. The current workflow does not require a separate live demonstration unless the professor requests one.

## Review and product-owner checklist

- GPT/Codex review and verification covers production extraction order, metadata validation, migration compatibility, embedding dimensions, idempotency/race handling, logs, and tests. Publish the task changes and evidence to GitHub for professor review.
- The production ingestion, metadata, embedding, retry, and 20-chunk review evidence above is available for Product Owner/professor review through GitHub after publication. Record Product Owner acceptance only if it is explicitly provided; a separate live demonstration is not required unless the professor requests one.

## Story 2 – Task 1: Internet ingestion

### Professor's Acceptance Criteria

1. "A web page, a remote PDF and one third-party repository's docs are ingested successfully."
2. "Boilerplate (navigation, footers, cookie banners) is removed."
3. "Source URL and fetch date are stored in the metadata."
4. "One failed source is logged and does not stop the rest of the batch."

### Supported sources and behavior

The existing `POST /api/ingestion/dynamic` endpoint accepts a source URL in `websiteUrl` and an optional `explicitVersion`. The `POST /api/ingestion/dynamic/batch` endpoint accepts up to four independent source requests by default and reports an outcome for each. Its effective limit is configurable with `INGESTION_MAX_BATCH_SOURCES` (maximum allowed configuration: 100). It supports:

```json
{
  "websiteUrl": "https://github.com/OWNER/REPOSITORY/tree/BRANCH/docs",
  "explicitVersion": "BRANCH"
}
```

Use the same request shape with a web-page URL or a direct `.pdf` URL.

Batch request example:

```json
{
  "sources": [
    { "websiteUrl": "https://docs.example.org/guide/", "explicitVersion": "1.0" },
    { "websiteUrl": "https://files.example.org/guide.pdf", "explicitVersion": "1.0" },
    { "websiteUrl": "https://github.com/OWNER/REPOSITORY/tree/BRANCH/docs", "explicitVersion": "BRANCH" }
  ]
}
```

- HTML pages, crawled within the starting host and path scope. HTML extraction removes script/style content, navigation/header/footer/aside/form elements, and common cookie/consent banners, then prefers `main`, `article`, or the remaining body text.
- Direct remote PDF URLs ending in `.pdf`. The fetcher requires a PDF response or `%PDF-` signature, limits the response to 5 MiB, and extracts searchable text with PDFBox.
- Public GitHub repository documentation directories using a URL such as `https://github.com/OWNER/REPOSITORY/tree/BRANCH/docs`, or an individual documentation file under `docs` using a GitHub `blob` URL. The service reads the GitHub repository tree and ingests Markdown, MDX, reStructuredText, text, and HTML files beneath the selected path, up to the configured `max-pages` limit. A branch name is used as the version when `explicitVersion` is omitted.

Dynamic crawl limits are configured with `INGESTION_MAX_PAGES` (default `100`) and `INGESTION_REQUEST_TIMEOUT_SECONDS` (default `20`). Usage limits are `INGESTION_MAX_BATCH_SOURCES` (default `4`), `INGESTION_MAX_EXTRACTED_CHARACTERS` (default `100000` per source), and `INGESTION_MAX_CHUNKS_PER_SOURCE` (default `200`). HTML responses and repository files are individually limited to 5 MiB; remote PDFs are limited to 5 MiB. The service rejects over-limit content without truncating and treating it as successful. Docker Compose passes these values to `ingest-dynamic`; names and defaults are listed in `.env.example`.

Incomplete crawls, repository fetch failures, and limit violations fail that source before document/version/chunk persistence. Batch source failures are logged and represented in that source's result, while processing continues with the next source. A failed source creates no document/version/chunk records unless a later database-stage failure occurs. New-version chunk insertion is transactional; existing versions are rejected before any chunk write. Application logs omit URL user-info, query parameters, and fragments, and do not include document text.

An unchanged version is skipped only when the version checksum matches and a read-only chunk fingerprint check finds the expected contiguous chunk indexes and content checksums in PostgreSQL. If a source document already exists, only that fully matching version is skipped; every other existing-source case returns an explicit conflict before embedding or persistence. A new source uses a create-only document request. Existing chunks are never repaired or replaced implicitly. A retry after an uncertain database failure should still be preceded by a read-only check of the document/version/chunk state.

### Dynamic chunk metadata

Each newly persisted dynamic chunk keeps the shared Story 1 fields `doc_id`, `version`, `source`, `page`, `chunk_index`, and `checksum`. It also stores `fetch_date` as an ISO-8601 UTC timestamp generated from `Instant` (for example, `2026-11-26T12:00:00Z`). The timestamp is present both at the metadata root and inside the `page` object. `source` and `page.url` identify the fetched page or raw repository file URL. A dynamic remote PDF is extracted one physical page at a time; each resulting chunk records that one-based page as `page.pdf_page_number` and identifies the PDF filename and MIME type. This keeps original PDF page provenance while satisfying the existing dynamic WEB metadata constraint.

The running project database currently has 11 pre-existing web chunks without `fetch_date`. Their historical fetch times are not recorded by the old ingestion implementation, so the new code does not invent dates or modify those rows. Any historical remediation needs a separately approved, documented timestamp policy.
### Read-only source preflight

The production crawler preflight fetched `https://docs.spring.io/spring-framework/reference/core.html` as version `7.1.0` (1 page, 909 extracted characters, 1 chunk). A separate read-only cleanup check reduced the page body text from 14,157 to 909 characters; visible text in four navigation elements, one footer, one header, and two asides did not remain, while the page title and Core Technologies prose remained. That page had no cookie-selector text to remove; cookie-banner removal is covered by automated fixtures. The original Oracle URL `https://download.oracle.com/javaee/6/firstcup/doc/firstcup.pdf` returns HTTP 302 to a different origin and is rejected by the dynamic fetcher. The direct candidate `https://docs.oracle.com/javaee/6/firstcup/doc/firstcup.pdf` returned HTTP 200, `application/pdf`, and 744,452 bytes. Running that URL through the compiled production `WebsiteFetcher`, `WebsiteCrawler`, and `DynamicTextChunker` with the configured limits produced 50 physical pages, 47 retained non-empty pages, 82,365 extracted characters, 115 page-aware chunks, and no failures. Pages 10, 24, and 48 contained only standalone printed page numbers and are treated as blank footer artifacts; later pages retain their original physical page numbers. Page 1 contains genuine 89-character title metadata. The `.invalid` failure source fails at DNS resolution, as intended. Its version is `1`, the default used by `WebsiteCrawler.crawlRemotePdf` when no explicit version is supplied. The pinned MkDocs user-guide scope `https://github.com/mkdocs/mkdocs/tree/2862536793b3c67d9d83c33e0dd6d50a791928f8/docs/user-guide` produced 9 files, 95,435 characters, and 123 chunks without failures. The full pinned `/docs` tree exceeded the 100,000-character cap and was rejected; the production request therefore uses the bounded `docs/user-guide` subtree at the same immutable commit. These are source/extraction preflight results only, not database, embedding, or production-ingestion evidence.




### Tests and verification status

The full dynamic-ingestion module suite passed freshly on 2026-11-27: 126 tests, 0 failures, 0 errors, 0 skipped. The document-service regression suite passed with 15 tests, 0 failures, 0 errors, 0 skipped. These runs include PDF per-page provenance, standalone-page-number cleanup, source conflict protection, batch failure-isolation, configuration binding, bounded retry, interruption, and sanitized 429-diagnostic tests.

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| ingest-dynamic | 126 | 0 | 0 | 0 |
| document-service | 15 | 0 | 0 | 0 |

Automated extraction, metadata serialization, bounded input, batch failure behavior, and conflict handling without chunk deletion are covered. Production persistence for the three source types remains unverified until the approval-gated run below is completed.

Run the suite from the repository root:

```powershell
.\mvnw.cmd -f ingest-dynamic\pom.xml test
```

The tests use generated PDFs, local HTTP servers, mocked dependencies, and a local GitHub API fixture. They verify extraction, metadata serialization, and failure isolation, but they do not prove production database persistence or billable embedding generation. Production ingestion of the three source types and database verification remain pending approval because that operation writes to the configured database and calls Gemini.

Focused test cases include `shouldExtractContentFromMainAndRemoveUnwantedElements`, `shouldExtractRemotePdfAsAnInternetSource`, `fetchesRepositoryMarkdownDocumentsAndUsesBranchAsVersion`, `recordsOneFailedDocumentAndContinuesFetchingOtherDocuments`, `shouldRejectPartialCrawlWithoutPersistingTruncatedSource`, `shouldRejectConflictingExistingVersionWithoutReplacingChunks`, and `shouldContinueBatchAfterOneSourceFails`. The log-sanitization test is `removesCredentialsQueryAndFragmentBeforeLogging`.

### Common Baseline

1. "Code is peer-reviewed and merged to the main branch."
2. "Automated tests pass, with at least one test for the success path and one for a failure path."
3. "No secrets in the repository, and configuration is documented."
4. "Errors and key events are logged."
5. "The README or docs are updated, and the result is demonstrated to the product owner."

GPT/Codex reviewed the implementation and focused test changes. The automated module tests pass as reported above. Runtime ingestion, database evidence, and GitHub publication are still outstanding; no Product Owner acceptance is claimed.
