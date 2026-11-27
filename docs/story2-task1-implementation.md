# Story 2 - Task 1: Internet ingestion

## Professor's Acceptance Criteria

1. "A web page, a remote PDF and one third-party repository's docs are ingested successfully."
2. "Boilerplate (navigation, footers, cookie banners) is removed."
3. "Source URL and fetch date are stored in the metadata."
4. "One failed source is logged and does not stop the rest of the batch."

## Implementation and limits

The dynamic service supports path-scoped HTML crawling, direct PDF download and PDFBox text extraction, public GitHub `tree` and `blob` documentation URLs, and `POST /api/ingestion/dynamic/batch` with per-source outcomes.

The default usage bounds are:

| Setting | Default | Effect |
|---|---:|---|
| `INGESTION_MAX_PAGES` | 100 | Maximum HTML pages or repository documentation files per source. |
| `INGESTION_REQUEST_TIMEOUT_SECONDS` | 20 | Timeout per outbound request. |
| `INGESTION_MAX_BATCH_SOURCES` | 4 | Maximum independent sources in one batch. |
| `INGESTION_MAX_EXTRACTED_CHARACTERS` | 100000 | Maximum combined extracted characters per source; exceeding it fails the source without persisting a partial result. |
| `INGESTION_MAX_CHUNKS_PER_SOURCE` | 200 | Maximum chunks sent for one source. |
| Response/file bytes | 5 MiB | Maximum HTML response, repository file, or PDF download. |

The chunk defaults are `CHUNK_SIZE=1000` and `CHUNK_OVERLAP=200`. Embeddings use `models/gemini-embedding-001`, 1536 dimensions, and batches of at most 64 chunks per API request. Three successful sources can therefore cause at most 12 embedding requests under the chunk cap. The hard extracted-character limit bounds these three sources to 300,000 characters total; the chunk cap also limits each source to 200 chunks. Actual billable token counts depend on Gemini tokenization and must be obtained from provider usage records. At the standard rate of $0.15 per million input tokens, 300,000 characters would cost about $0.045 if every character counted as one token; a rough four-characters-per-token estimate is about $0.011. These are estimates, not guaranteed billing bounds; pricing tier and tokenization can change them.

### Gemini diagnostics and bounded retry policy

The production 429 boundary was established at the Gemini embedding request after Oracle PDF extraction and MkDocs crawling. The provider's exact quota or rate-limit cause remains unknown because the previous implementation retained only the HTTP status. `GeminiEmbeddingService` now records a bounded, sanitized diagnostic containing the status, provider error code/message when parseable, `Retry-After`, allowlisted rate-limit headers, and a provider request ID when present. Error bodies are bounded and sensitive fields such as API keys, authorization values, tokens, passwords, and secrets are redacted; raw bodies and request headers are never logged.

Retries remain disabled by default with `EMBEDDING_MAX_ATTEMPTS=1`. If provider evidence and an explicit operational decision justify retries, the bounded settings are `EMBEDDING_MAX_ATTEMPTS` (maximum 5), `EMBEDDING_INITIAL_BACKOFF_MILLIS`, `EMBEDDING_MAX_BACKOFF_MILLIS` (maximum 60 seconds), and `EMBEDDING_JITTER_RATIO` (0 to 1). Only 408, 425, 429, 500, 502, 503, and 504 are retryable; permanent client errors are not retried. A valid `Retry-After` value is honored within the configured maximum, exponential backoff is capped, and interruption restores the thread interrupt flag and aborts the batch. Source sequencing, failure isolation, persistence ordering, `models/gemini-embedding-001`, 1536 dimensions, and PostgreSQL `VECTOR(1536)` compatibility are unchanged. These diagnostics and tests do not establish that the provider issue is resolved; production ingestion remains blocked pending provider quota evidence and explicit approval.

Remote PDFs are extracted one physical PDF page at a time with PDFBox. Blank pages produce no chunks; non-empty pages retain their original one-based PDF page number through chunking. The aggregate extracted-character limit is enforced across all extracted pages. Each resulting chunk therefore maps to exactly one source PDF page; chunk overlap is applied within that page and never crosses page boundaries.

## Metadata and recovery

New chunks retain the shared metadata fields `doc_id`, `version`, `source`, `page`, `chunk_index`, and `checksum`, and add `fetch_date` as an ISO-8601 UTC timestamp at the metadata root and in the page object. The source URL is recorded in `source` and `page.url`. For HTML and repository documentation, `page` remains the existing web object. For a remote PDF, the dynamic document remains `source_type=WEB` because V3 requires dynamic records to use that schema; its `page` object additionally contains `content_type: application/pdf` and `pdf_page_number` with the original one-based page number. The SQL `page_number` column remains NULL for dynamic PDF records to satisfy V3, while the JSON metadata preserves page provenance. Static PDF ingestion continues to store its one-based page as the numeric `page` value and SQL `page_number`.

An unchanged version is skipped only if its version checksum matches and PostgreSQL contains the expected contiguous chunk indexes with matching content checksums. The service reads existing documents before writing and uses a create-only document request for a new source. If a source document already exists, a version whose content matches is either skipped (when every chunk has a vector for the configured provider) or has only its missing provider vectors filled. That fill applies when chunk content matches but, for example, `embedding_ollama` is NULL on a chunk first stored with Gemini. The fill updates only `WHERE embedding_ollama IS NULL`, and content, metadata and other vectors are never rewritten. Every other existing-source case produces an explicit conflict before embedding or persistence; content is never overwritten or chunk-replaced.

**Recovery from a half-finished ingest.** Document creation, version creation and chunk insertion are separate calls. If chunk insertion fails after the version row exists, the version is left with zero chunks, and every later run for that source reports a conflict. This is deliberate: the service never deletes data on its own. To recover, confirm that the version has no chunks (`SELECT count(*) FROM chunks WHERE version_id = <id>` returns 0), delete that empty version, and also delete the document if it has no other versions. Then re-run the source. Each batch source is isolated from failures in other sources, while a partial/incomplete crawl itself is rejected before persistence rather than reported as successful.

Eleven previously observed web chunks have no `fetch_date`; the former ingestion path did not record those timestamps. No historical values are fabricated or backfilled by this task.

## Proposed production batch

The selected GitHub source is limited to the `docs/user-guide` subtree because the complete `/docs` tree exceeds the 100,000-character cap and correctly fails as an incomplete source. It remains pinned to the verified immutable MkDocs commit `2862536793b3c67d9d83c33e0dd6d50a791928f8`:

`https://github.com/mkdocs/mkdocs/tree/2862536793b3c67d9d83c33e0dd6d50a791928f8/docs/user-guide`

Read-only production-crawler preflight at that commit returned 9 documentation files, 95,435 extracted characters, 123 chunks, and no failures. The top-level `/docs` scope returned a limit failure after the fetched content crossed 100,000 characters; it is not treated as successful.

The complete proposed batch, in order, is:

1. Deliberate failure: `https://story2-task1-source-failure.invalid/docs/`, explicit version `1`.
2. Web page: `https://docs.spring.io/spring-framework/reference/core.html`, explicit version `7.1.0`; preflight 1 page, 909 characters, 1 chunk.
3. Remote PDF candidate: `https://docs.oracle.com/javaee/6/firstcup/doc/firstcup.pdf`, explicit version `1`; the production crawler defaults this PDF source to version `1`. The original `download.oracle.com` URL returns a cross-origin redirect and is rejected. The direct candidate returned HTTP 200 with `application/pdf` and 744,452 bytes. Production-equivalent local preflight produced 50 physical pages, 47 retained non-empty pages, 82,365 normalized characters, 115 page-aware chunks, and no failures. Pages 10, 24, and 48 contained only standalone printed page numbers and are now treated as blank footer artifacts; physical page provenance remains one-based and later pages retain their original numbers. Page 1 contains genuine 89-character title metadata.
4. Third-party repository docs: the pinned MkDocs `docs/user-guide` URL above, explicit version `2862536793b3c67d9d83c33e0dd6d50a791928f8`.

The Spring page body contained 14,157 text characters before cleanup and 909 after cleanup. Text from its navigation/header/footer/aside elements was absent from the cleaned result and its title and main Spring prose were retained; the page had no visible cookie-selector content. Cookie-banner cleanup is verified by automated HTML fixtures. These preflight results demonstrate that each valid source fits current extraction/chunk limits. They are not persistence, embedding, or production-batch evidence. The batch is limited to four source requests; the failure source is first so later valid sources can demonstrate continuation.
## Local verification

The command `.\mvnw.cmd -f ingest-dynamic\pom.xml test` passed freshly on 2026-11-27: 126 tests, 0 failures, 0 errors, 0 skipped. The command `.\mvnw.cmd -f document-service\pom.xml test` passed with 15 tests, 0 failures, 0 errors, 0 skipped. The suites include PDF extraction page provenance, standalone-page-number cleanup, source conflict protection, metadata serialization, boilerplate cleanup, batch failure-isolation, configuration binding, bounded retry, interruption, and sanitized 429-diagnostic tests. Tests use local fixtures/mocks and do not prove production database persistence or Gemini embedding generation.

Production ingestion was attempted once on 2026-11-26 under explicit approval; only the Spring source succeeded. Oracle and MkDocs failed during embedding with HTTP 429, and post-attempt database verification is recorded below. The separately approved targeted Oracle/MkDocs retry also returned HTTP 429 for both sources and left the database unchanged. No further retry, repair, or GitHub publication is authorized by this task.

## Common Baseline

1. "Code is peer-reviewed and merged to the main branch."
2. "Automated tests pass, with at least one test for the success path and one for a failure path."
3. "No secrets in the repository, and configuration is documented."
4. "Errors and key events are logged."
5. "The README or docs are updated, and the result is demonstrated to the product owner."

GPT/Codex review is the project's accepted practical review process. Code review and local verification do not constitute GitHub publication or Product Owner acceptance. No Product Owner demonstration or acceptance is claimed here.
## Evidence-based checklist

### Professor acceptance criteria

- [ ] **AC1 local implementation and preflight only:** HTML page, direct remote PDF candidate, and pinned GitHub repository documentation each produce pages and chunks in the production-equivalent crawler/chunker path. Production persistence remains approval-gated.
- [ ] **AC2 local implementation and automated verification only:** navigation, header/footer/aside/form elements, scripts/styles, common cookie/consent selectors, and standalone printed PDF page-number artifacts are removed or skipped; fixtures pass. Production persistence remains unverified.
- [ ] **AC3 local implementation and automated verification only:** new dynamic chunks retain the six-field Story 1 contract and add `source_url` plus UTC `fetch_date` at the metadata root and page object. Eleven historical web chunks remain without fabricated dates.
- [ ] **AC4 local implementation and automated verification only:** batch source failures are logged, isolated, and followed by later sources; the fresh suite includes the failure-continuation path. Production batch execution remains approval-gated.

### Common Baseline

- [ ] **1. Peer-reviewed and merged to `main`:** changes remain unstaged and uncommitted on `main`.
- [x] **2. Automated tests:** fresh `ingest-dynamic` run passed with 126/126 tests and the document-service regression suite passed with 15/15; both include success and failure paths.
- [x] **3. No secrets and documented configuration:** `.env` remains ignored/untracked; configuration is documented in `.env.example` and the README.
- [x] **4. Errors and key events logged:** crawler, fetcher, batch, persistence, and controller paths emit sanitized success/failure events.
- [ ] **5. Documentation and Product Owner demonstration:** README and this implementation record are updated locally; GitHub publication and Product Owner acceptance remain pending.

## Current read-only production-readiness record

The exact four-source preflight was rerun on 2026-11-26 with the compiled production crawler and chunker after the standalone-page-number fix:

| Source | Version | Result | Extracted content | Chunks | Notes |
|---|---|---|---:|---:|---|
| `https://docs.spring.io/spring-framework/reference/core.html` | `7.1.0` | complete | 909 characters, 1 retained page | 1 | Main content retained; navigation/header/footer/aside text removed. |
| `https://docs.oracle.com/javaee/6/firstcup/doc/firstcup.pdf` | `1` | complete | 82,365 characters, 50 physical pages / 47 retained pages | 115 | Pages 10, 24, and 48 were standalone printed page numbers and were skipped; later pages retained physical numbers. |
| `https://github.com/mkdocs/mkdocs/tree/2862536793b3c67d9d83c33e0dd6d50a791928f8/docs/user-guide` | `2862536793b3c67d9d83c33e0dd6d50a791928f8` | complete | 95,435 characters, 9 retained files | 123 | Immutable commit and bounded `docs/user-guide` scope verified. |
| `https://story2-task1-source-failure.invalid/docs/` | `1` | failed as expected | DNS resolution failure | 0 | Failure is isolated by the batch service; no source result is successful. |

The three successful sources total **239 chunks**. The service embeds each source independently with a batch size of 64, so the expected Gemini request count is `ceil(1/64) + ceil(115/64) + ceil(123/64) = 5`. The current preflight chunk text volume is approximately 215,009 characters. Using the project’s documented $0.15 per million input-token estimate, a rough four-characters-per-token estimate is about 53,752 tokens and $0.008; a conservative one-character-per-token upper estimate is about 215,009 tokens and $0.033. These are estimates only; provider tokenization, pricing tier, and usage accounting can differ. No Gemini request was made during preflight.

The pre-attempt read-only PostgreSQL baseline was 8 documents, 8 versions, and 74 chunks. The post-attempt read-only state is 9 documents, 9 versions, and 75 chunks, all with 1536-dimensional embeddings. The only new persisted source is Spring Framework document 11, version 10, with one chunk and UTC fetch metadata. Oracle, MkDocs, and the `.invalid` source have no records. Eleven historical WEB chunks still have no `fetch_date`; no historical values were fabricated. An unrelated duplicate `documents.source_uri` for `https://docs.spring.io/spring-boot/` remains outside the approved source set.

The approved batch response reported `sourceCount=4`, `succeeded=1`, and `failed=3`. The deliberate `.invalid` source failed with DNS resolution. Spring succeeded with one chunk. Oracle PDF extraction completed at 50 physical pages and 82,365 characters, then its embedding request returned HTTP 429. MkDocs fetched 9 files, then its embedding request returned HTTP 429. Existing logs do not include provider response headers such as `Retry-After`, so the exact quota/rate-limit cause is not observable from retained evidence. The sequence and code path establish that both 429s originated at the Gemini embedding call after source fetching, not at Oracle or GitHub persistence.

The running `devdocs-ingest-dynamic` and `devdocs-document-service` containers were rebuilt and redeployed after the configuration-binding fix and remained healthy during the approved batch. The approved operational command was:

```powershell
docker compose build ingest-dynamic document-service
docker compose up -d ingest-dynamic document-service
```

This targeted the dynamic-ingestion and document-service images so the create-only conflict protection was deployed together. It was executed before the batch. The batch was not retried after the valid-source failures.

Exact proposed request payload:

```json
{
  "sources": [
    { "websiteUrl": "https://story2-task1-source-failure.invalid/docs/", "explicitVersion": "1" },
    { "websiteUrl": "https://docs.spring.io/spring-framework/reference/core.html", "explicitVersion": "7.1.0" },
    { "websiteUrl": "https://docs.oracle.com/javaee/6/firstcup/doc/firstcup.pdf", "explicitVersion": "1" },
    { "websiteUrl": "https://github.com/mkdocs/mkdocs/tree/2862536793b3c67d9d83c33e0dd6d50a791928f8/docs/user-guide", "explicitVersion": "2862536793b3c67d9d83c33e0dd6d50a791928f8" }
  ]
}
```

## Full individual TODO checklist

### AC1 — Three source types

1. **Select sources:** complete; the exact Spring, Oracle direct PDF, and pinned MkDocs sources are recorded above.
2. **End-to-end production ingestion:** pending explicit approval; local crawler/chunker preflight passes.
3. **Download, processing, chunking, embedding, persistence:** download/processing/chunking locally verified; Gemini and PostgreSQL remain pending.
4. **Database records:** pending production run and read-only post-run verification.
5. **Automated success and failure tests:** complete; the fresh 126-test dynamic suite and 15-test document-service suite include both paths.
6. **GPT/Codex review:** local review complete; publication and external review remain pending.

### AC2 — Boilerplate removal

1. **Before/after cleanup inspection:** complete for Spring preflight and local fixtures.
2. **Navigation/footer/cookie removal:** complete in implementation and fixtures; Spring had no visible cookie banner.
3. **Main content preservation:** complete in fixtures and Spring preflight.
4. **Cleanup failure and no-boilerplate tests:** complete; empty cleanup and content-without-boilerplate paths are covered.
5. **GPT/Codex review:** complete locally; production representative-chunk review pending.

### AC3 — Metadata

1. **Source URL metadata:** complete in serializer and tests; production verification pending.
2. **Fetch-date metadata:** complete in serializer and tests; production verification pending.
3. **Web/PDF/repository metadata:** source-specific structures locally verified; production verification pending.
4. **Valid/missing/invalid URL/date tests:** URL validation and metadata validation tests pass; production verification pending.
5. **GPT/Codex review:** complete locally; publication remains pending.

### AC4 — Batch failure isolation

1. **Multi-source batch with a failed source:** complete in automated fixtures; exact production payload prepared.
2. **Failure logging:** complete locally with sanitized URL logging; production log evidence pending.
3. **Remaining sources continue:** complete in automated tests; production evidence pending.
4. **Per-source results:** complete in DTO/controller/service tests; production response pending.
5. **Production logs:** pending approved run.
6. **GPT/Codex review:** complete locally; publication remains pending.

### Common Baseline individual items

1. **Code review and findings resolved:** local review complete.
2. **Code merged/published to `main`:** pending explicit Git approval.
3. **Success-path test:** complete; fresh suite passes.
4. **Failure-path test:** complete; fresh suite passes.
5. **Full relevant suites:** complete; ingest-dynamic 126/126 and document-service 15/15 passed.
6. **No secrets in repository/history:** local scan found no high-confidence token patterns; publication review still pending.
7. **Configuration documented:** complete in `.env.example`, application configuration, Compose, README, and this record.
8. **Errors and key events logged:** complete in local implementation/tests; production evidence pending.
9. **README/docs updated:** complete locally.
10. **Product Owner demo prepared:** pending final production evidence package.
11. **Product Owner demo performed and evidenced:** pending user demonstration.

## Product Owner demonstration plan

The demonstration remains approval-gated and has not been performed. After
the two service images are rebuilt and restarted with approval, show the
health endpoints, submit the exact four-source batch from the payload above,
and capture the per-source response and sanitized service logs. The expected
result is one DNS failure followed by successful Spring, Oracle PDF, and
MkDocs results. Use read-only SQL afterward to show document, version, chunk,
embedding, metadata, and PDF page-provenance checks. Record the before/after
counts and retain the response, logs, and SQL output as evidence for AC1-AC4.

**Readiness verdict: READY FOR EXPLICIT PRODUCTION APPROVAL, NOT DONE.** Local blockers are resolved. Production ingestion, Gemini usage, database writes, Docker rebuild/restart, Git publication, and Product Owner demonstration remain outstanding approval or evidence gates.

## Production ingestion under Ollama (2026-11-27)

This supersedes the Gemini 429 blocker above. After migration V4, the dynamic service runs with `EMBEDDING_PROVIDER=ollama` (`nomic-embed-text:137m-v1.5-fp16`, 768 dims, `chunks.embedding_ollama`). The exact four-source payload above was posted to `POST /api/ingestion/dynamic/batch` on the rebuilt containers, after a verified backup (`DevDocs-AI-backups/pre-story2-ollama-batch-20261127-180433.dump`).

| Source | Result run 1 | Document / version | Chunks | Run 2 |
|---|---|---|---:|---|
| `https://story2-task1-source-failure.invalid/docs/` | failed: "Website hostname could not be resolved." | none | 0 | failed (same) |
| Spring Framework `core.html` `7.1.0` | succeeded; existing Gemini chunk matched by checksum, its NULL Ollama vector filled | 11 / 10 | 1 | skipped |
| Oracle `firstcup.pdf` `1` | succeeded, new | 12 / 11 | 115 | skipped |
| MkDocs `docs/user-guide` @ `2862536…` | succeeded, new | 13 / 12 | 123 | skipped |

Response run 1: `sourceCount=4, succeeded=3, failed=1`, HTTP 200 in 13.9 s. The failing source was first and did not stop the later sources (**AC4**). Run 2 left a full-table fingerprint unchanged; it covered content, metadata, both vector columns and the Ollama timestamps.

**Database after the run.** There are 11 documents, 11 versions and 313 chunks (previously 9, 9 and 75). Gemini vectors: 75, unchanged; the fingerprint over the original 75 chunks equals the pre-V4 value `637317a5…`. Ollama vectors: 283, covering the 44 Story 1 chunks, the Spring chunk, and 238 new chunks. Thirty older chunks (documents 4, 5 and 8, outside both stories' sample sets) still have only Gemini vectors.

**AC1, three source types.** The web page, the remote PDF and the third-party repository docs are each persisted with Ollama vectors.

**AC3, provenance.** All 239 Story 2 chunks have an ISO-8601 UTC `fetch_date` at both the metadata root and `page.fetch_date`. Spring and Oracle `source_url` equal the document source URI. MkDocs `source_url` is the specific raw file URL at the pinned commit, covering 9 files. The Spring chunk keeps its original 2026-11-26 fetch date, because the backfill does not rewrite metadata. All 115 Oracle chunks have `page.content_type=application/pdf` and a one-based `pdf_page_number` spanning 47 distinct pages from 1 to 50. The missing pages, 10, 24 and 48, are the documented standalone page-number artifacts. SQL `page_number` stays NULL, as V3 requires.

**AC2, boilerplate.** Across the 239 chunks, marker searches found no cookie-consent, "skip to content" or "edit on GitHub" text. The single "All rights reserved" match is Oracle PDF page 2, which is the document's own legal-notice page and is retained as content.

## Review follow-ups (2026-11-27)

- **Truncated GitHub trees.** If GitHub's recursive tree response has `truncated: true`, the fetcher records a failure and fetches no files. The source is then incomplete and nothing is persisted, so a partial repository can no longer pass as complete.
- **Existing-source lookup.** `ingest-dynamic` finds an existing source with `GET /api/documents/web?sourceUri=…`, a new document-service endpoint that uses the V2 partial unique index and returns a list of zero or one documents. It no longer downloads every document. Verified live: the four-source batch re-ran with all three existing sources skipped and an unchanged database fingerprint.
