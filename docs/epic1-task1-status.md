# Epic 1: Task 1 of each story, acceptance and baseline status

Checked on 2026-11-27 against the professor's *tasks for first epic* (per-task criteria and the baseline) and the DevDocs AI proposal. Scope is Task 1 of Stories 1–4. Each task is done only when its own criteria and the baseline are both met.

## Story 1 Task 1: PDF ingestion

| Criterion | Status | Evidence |
|---|---|---|
| Sample PDFs incl. one with tables and one with multiple columns ingested end to end | ✅ | `compliance_responsibilities.pdf` (table) and ACL L16-1570 (two-column), re-ingested under Ollama. See [story1-task1-manual-review.md](story1-task1-manual-review.md). |
| Every chunk carries doc_id, version, source, page, chunk_index, checksum | ✅ | All 313 chunks in the DB have all six keys; `chk_chunks_metadata_contract` (V3) enforces this in PostgreSQL. |
| Chunk size, overlap and embedding model configurable and documented | ✅ | `CHUNK_SIZE`, `CHUNK_OVERLAP`, `EMBEDDING_PROVIDER`, `OLLAMA_EMBEDDING_MODEL` in `.env.example` and the [README](../README.md#configuration). |
| Re-running the same file creates no duplicates | ✅ | Two re-runs: chunk IDs unchanged and the second run made no change at all (fingerprints in the review doc). |
| Manual check of 20 random chunks shows no broken text | ✅ | Seeded 20-chunk review, 20/20 PASS. Content is byte-identical after the Ollama re-ingestion. |

## Story 2 Task 1: Internet ingestion

| Criterion | Status | Evidence |
|---|---|---|
| A web page, a remote PDF and one third-party repo's docs ingested | ✅ | Spring `core.html`, Oracle `firstcup.pdf` (115 chunks), pinned MkDocs `docs/user-guide` (123 chunks). See [story2-task1-implementation.md](story2-task1-implementation.md). |
| Boilerplate (navigation, footers, cookie banners) removed | ✅ | Extractor fixtures, plus marker search over the 239 persisted chunks. |
| Source URL and fetch date stored in metadata | ✅ | `source_url` and UTC `fetch_date` on every Story 2 chunk (root and `page`). |
| One failed source logged, rest of batch continues | ✅ | Live batch: `.invalid` failed (WARN), the other 3 succeeded. |

## Story 3 Task 1: Safe metadata filtering

| Criterion | Status | Evidence |
|---|---|---|
| URL params parsed into a validated filter object; raw strings never reach the DB | ✅ | `MetadataFilterParser` builds the typed `MetadataFilter`; SQL comes only from enum constants and every value is bound. A JSON-body filter is ignored. See [story3-task1-metadata-filtering.md](story3-task1-metadata-filtering.md). |
| Only whitelisted fields and operators accepted | ✅ | 8 fields with an operator matrix per field; parser tests. |
| Invalid filter → 400 listing the valid fields | ✅ | `FilterExceptionHandler`; endpoint tests and Postman items 07–09. |
| AND/OR works, with a committed Postman collection | ✅ | [`postman/DevDocs-Story3-Metadata-Filters.postman_collection.json`](../postman/DevDocs-Story3-Metadata-Filters.postman_collection.json): 11 requests, 27/27 checks passing live. |
| Null filter falls through to the standard flow | ✅ | Endpoint test and Postman item 01 (`appliedFilter: null`, versionId-scoped). |

## Story 4 Task 1: Reranker

| Criterion | Status | Evidence |
|---|---|---|
| Retrieve wider top-N (20), rerank to top-k (5) | ✅ | `candidateCount` = max(topK, 20); the response reports `candidates: 20, returned: 5`. |
| Golden set shows improvement in MRR or recall@k | ✅ | MRR 0.577 → 0.978, recall@1 0.283 → 0.883, recall@5 0.85 → 0.967 on 30 questions. See [story4-task1-reranker.md](story4-task1-reranker.md). |
| Model version pinned; added latency documented | ✅ | `mmarco-mMiniLMv2-L12-H384-v1@1427fd65`, TEI image pinned by digest. Latency p50 1.8 s / p95 2.2 s. |
| Reranker failure keeps original order | ✅ | Unit tests, plus a live run with the reranker stopped: HTTP 200, vector order, WARN log. |

## Baseline, per task

| Baseline item | S1T1 | S2T1 | S3T1 | S4T1 |
|---|:-:|:-:|:-:|:-:|
| Peer-reviewed and merged to `main` | ✅¹ | ✅¹ | ✅¹ | ✅¹ |
| Tests pass, incl. success and failure paths | ✅ | ✅ | ✅ | ✅ |
| No secrets; configuration documented | ✅ | ✅ | ✅ | ✅ |
| Errors and key events logged | ✅ | ✅ | ✅ | ✅ |
| README/docs updated | ✅ | ✅ | ✅ | ✅ |
| **Demonstrated to the Product Owner** | ⏳ | ⏳ | ⏳ | ⏳ |

⏳ The Product Owner demonstration is the one open item for all four tasks. The project owner must do it.

**Tests (all passing on 2026-11-27):**

| Module | Tests | Covers |
|---|---:|---|
| ingest-static | 44 | S1T1 |
| ingest-dynamic | 139 | S2T1 |
| document-service | 17 | S1T1, S2T1 |
| pre-retrieval-service | 76 | S3T1, S4T1 |
| post-retrieval-service | 14 | |

Success-path and failure-path examples:

| Task | Success path | Failure path |
|---|---|---|
| S1T1 | `ChunkPersistenceServiceTest` (persist, idempotent re-ingest) | Checksum mismatch, wrong source type |
| S2T1 | `DynamicIngestionServiceTest` (batch) | `.invalid` source isolated, oversize PDF, truncated GitHub tree |
| S3T1 | `FilterEndpointsTest` (AND/OR, null filter) | Invalid field / operator / mode returns 400 |
| S4T1 | `RerankingServiceTest` (reorder) | Reranker down, 5xx or bad JSON keeps vector order |

**Secret scan:** the diff `dbe9c23..HEAD` and all tracked files were scanned for API-key, token and private-key patterns. Nothing was found. `.env` is git-ignored.

## Review record

¹ This is an individual project. The proposal (§11 Q6) asks the Product Owner what alternative to peer review is acceptable, and the review used here should be confirmed with the Product Owner. Review steps used:

1. Self-review of each change before commit.
2. An independent automated code review of `dbe9c23..HEAD` (all four tasks plus the V4 migration). It had a read-only brief and no context from the implementation session. Result: **no blockers**, and four should-fix findings, all fixed and pushed:

   | Finding | Fix |
   |---|---|
   | A lone `filter` with commas (e.g. `document_id:in:12,13`) was split by Spring and rejected | Read values via `getParameterValues`; regression test and Postman item 11 |
   | Over 32 reranker candidates exceeded the TEI batch limit and silently fell back | Candidates capped at 32, any tail keeps vector order, TEI flag made explicit |
   | A remote PDF over 5 MiB could be truncated and parsed as complete | PDFs that reach the cap are rejected |
   | A half-finished ingest leaves an empty version, so later runs conflict | Manual recovery procedure documented |

   Nits fixed: truncated GitHub tree listings now fail the source, and existing-source lookup uses an indexed endpoint instead of listing every document.

## Deviations from the proposal to mention in the demo

- **Reranker location:** the cross-encoder runs in `pre-retrieval-service`, where retrieval and the existing ranking step already lived. The professor's diagram puts reranking in the retrieval-post service. The behaviour required by the criteria does not depend on which service hosts it.
- **Module names:** the services are `pre-retrieval-service` / `post-retrieval-service` rather than `retrieval-pre` / `retrieval-post`.
- **No Spring AI:** embeddings and LLM calls use Spring `RestClient` directly (Ollama for embeddings, Cloudflare Workers AI for generation, a TEI cross-encoder) rather than Spring AI. The proposal lists Spring AI in its stack.
- **Golden set:** the 30 questions (Vietnamese) cover one Spring Boot 4.2 document version (11 chunks). Unanswerable questions belong to Story 4 Task 4, which is outside this Task 1 scope.
