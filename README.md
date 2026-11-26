# DevDocs AI

Version-aware retrieval-augmented assistant for Java and Spring developers.

## PDF and web ingestion

PDF ingestion is handled by `ingest-static`: the upload endpoint validates the PDF, extracts each page with PDFBox, normalizes line-end hyphenation, removes repeated running headers/standalone page numbers, formats positioned cells, chunks the page text, creates Gemini document embeddings, and persists chunks and metadata to PostgreSQL with pgvector. Chunks remain associated with their one-based source page. Web ingestion is handled by `ingest-dynamic` and uses the same embedding model/dimension contract.

PDF layout reconstruction uses text positions. Repeated large gutters are treated as two-column text flow and read down the left column before the right; multiple aligned cell gaps are kept row-wise and separated with ` | `. This is deterministic heuristic layout handling, not OCR or a full table understanding engine. Image-only PDFs, complex nested tables, rotated text, and ambiguous page layouts can still require review.

## Metadata contract

Every newly persisted chunk must include `doc_id`, `version`, `source`, `page`, `chunk_index`, and `checksum`. `source_type` is also stored to distinguish the page representation:

- PDF chunks use a positive, one-based integer for `page`, matching the database `page_number` column.
- Web chunks use an object such as `{"type":"web","url":"https://docs.example/page","title":"Page title"}`. The database `page_number` remains SQL `NULL` for web content.
- `checksum` is the lower-case SHA-256 checksum of the exact chunk text. `chunk_index` is zero-based within the document version.

Migration `V3__enforce_chunk_metadata_contract.sql` backfills existing rows from their authoritative document/version/chunk columns, then adds a validated check constraint. For web rows, the backfill uses the stored source URL and page title to create the web `page` object. This repository change does not alter a database. Do not apply the migration to a shared database without approval and a backup/rollback plan.

## Configuration

The compose file reads `.env` from the project root. Copy `.env.example` to `.env` locally and fill secrets only in the ignored local file. Never commit or print its contents.

| Variable | Default | Purpose |
|---|---|---|
| `CHUNK_SIZE` | `1000` | Maximum target chunk size in characters. |
| `CHUNK_OVERLAP` | `200` | Character overlap between chunks; must be smaller than chunk size. |
| `GEMINI_API_URL` | Gemini batch embedding endpoint | Embedding API endpoint. |
| `GEMINI_API_KEY` | unset | Secret API credential; required for real embedding requests. |
| `EMBEDDING_MODEL` | `models/gemini-embedding-001` | Gemini embedding model. |
| `EMBEDDING_DIMENSIONS` | `1536` | Must remain 1536 because PostgreSQL uses `VECTOR(1536)`. |
| `EMBEDDING_BATCH_SIZE` | `64` | Number of chunks per embedding request batch. |
| `POSTGRES_DB` | `devdocs` | Local database name. |
| `POSTGRES_USER` | `devdocs` | Local database user. |
| `POSTGRES_PASSWORD` | unset in example | Set a local database password in `.env`. |

The embedding dimension is intentionally constrained to 1536 in both ingestion services, the vector serializer, and the current database schema. A different dimension requires a coordinated schema migration and re-embedding plan; changing only the environment variable is rejected.

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

### Automated test results

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
