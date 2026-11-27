# Story 1 – Task 1: Manual PDF chunk review

Review date: 2026-11-26

## Sample and selection

The review selected 20 persisted PDF chunks from the two production-ingested sample versions (44 eligible chunks total). Selection is reproducible with PostgreSQL ordering by:

```sql
ORDER BY md5(chunk_id::text || ':story1-task1-ac5-seed-20261126-01')
LIMIT 20
```

The table sample is `compliance_responsibilities.pdf`, document 9, version 8 (`v1`), four pages and five persisted chunks. Its source file is the available IAPC QA sample `qa-over-internal-docs/compliance_responsibilities.pdf`; page 1 visibly contains a framework mapping table.

The separate prose-layout sample is *Text Segmentation of Digitized Clinical Texts*, document 10, version 9 (`LREC-2016`), eight pages and 39 persisted chunks. The PDF was obtained from the ACL Anthology at <https://aclanthology.org/L16-1570.pdf>. Its first page has two columns of prose, and the source publication describes the two-column layout. The ACL Anthology identifies this 2016 material as CC BY 4.0.

For every selected row, the stored text was compared with the production extraction response for the same page and chunk index. Its UTF-8 SHA-256 was recalculated and matched both the chunk checksum and the response checksum. The complete source pages were visually inspected: the table page and ACL pages 1, 3, 4, 5, 6, 7, and 8. No chunk text is reproduced here; excerpts are omitted to avoid copying document content into the report.

## Results

| Chunk ID | Document/version | PDF page | Chunk index | Result | Findings |
|---:|---|---:|---:|---|---|
| 40 | 9 / 8 (`v1`) | 1 | 1 | PASS | Matches production page text and checksum; table page inspected. |
| 71 | 10 / 9 (`LREC-2016`) | 6 | 27 | PASS | Matches production page text and checksum; source page inspected. |
| 55 | 10 / 9 (`LREC-2016`) | 3 | 11 | PASS | Matches production page text and checksum; source page inspected. |
| 66 | 10 / 9 (`LREC-2016`) | 5 | 22 | PASS | Matches production page text and checksum; source page inspected. |
| 62 | 10 / 9 (`LREC-2016`) | 4 | 18 | PASS | Matches production page text and checksum; source page inspected. |
| 39 | 9 / 8 (`v1`) | 1 | 0 | PASS | Matches production page text and checksum; table page inspected. |
| 64 | 10 / 9 (`LREC-2016`) | 5 | 20 | PASS | Matches production page text and checksum; source page inspected. |
| 76 | 10 / 9 (`LREC-2016`) | 7 | 32 | PASS | Matches production page text and checksum; source page inspected. |
| 72 | 10 / 9 (`LREC-2016`) | 7 | 28 | PASS | Matches production page text and checksum; source page inspected. |
| 67 | 10 / 9 (`LREC-2016`) | 5 | 23 | PASS | Matches production page text and checksum; source page inspected. |
| 78 | 10 / 9 (`LREC-2016`) | 8 | 34 | PASS | Matches production page text and checksum; source page inspected. |
| 74 | 10 / 9 (`LREC-2016`) | 7 | 30 | PASS | Matches production page text and checksum; source page inspected. |
| 79 | 10 / 9 (`LREC-2016`) | 8 | 35 | PASS | Matches production page text and checksum; source page inspected. |
| 53 | 10 / 9 (`LREC-2016`) | 3 | 9 | PASS | Matches production page text and checksum; source page inspected. |
| 75 | 10 / 9 (`LREC-2016`) | 7 | 31 | PASS | Matches production page text and checksum; source page inspected. |
| 77 | 10 / 9 (`LREC-2016`) | 7 | 33 | PASS | Matches production page text and checksum; source page inspected. |
| 60 | 10 / 9 (`LREC-2016`) | 4 | 16 | PASS | Matches production page text and checksum; source page inspected. |
| 82 | 10 / 9 (`LREC-2016`) | 8 | 38 | PASS | Matches production page text and checksum; source page inspected. |
| 68 | 10 / 9 (`LREC-2016`) | 5 | 24 | PASS | Matches production page text and checksum; source page inspected. |
| 44 | 10 / 9 (`LREC-2016`) | 1 | 0 | PASS | Matches production page text and checksum; two-column reading order inspected. |

## Quality checks

- 20 distinct persisted chunks reviewed; 20 PASS, 0 FAIL.
- All selected chunks matched the current production extraction for their page and index.
- Recomputed chunk SHA-256 checksums matched all 20 persisted checksum values.
- No replacement characters (`U+FFFD`) or checked mojibake patterns were found.
- The two-column paper's page 1 extraction follows the left-column introduction into the right-column continuation and then the next section in reading order.
- The table sample's page 1 extraction retained the table heading/column fields.
- No source text excerpts are included in this report.

## Ollama re-verification (2026-11-27)

The evidence above was produced on the Gemini path (`chunks.embedding VECTOR(1536)`). After migration V4 added `chunks.embedding_ollama VECTOR(768)`, both samples were re-ingested through the running `ingest-static` container with `EMBEDDING_PROVIDER=ollama` (`nomic-embed-text:137m-v1.5-fp16`).

**Inputs.** The same two source files were used; their SHA-256 values match the registered version checksums:

| Sample | Document / version | SHA-256 (matches `document_versions.checksum`) |
|---|---|---|
| `compliance_responsibilities.pdf` | 9 / 8 (`v1`) | `aeb36cf8…63233059` |
| ACL L16-1570 two-column paper | 10 / 9 (`LREC-2016`) | `685c341a…ce626765` |

**Procedure.** A verified `pg_dump` backup was taken first (`DevDocs-AI-backups/pre-v4-ollama-20261127-174808.dump`, restored into an isolated container with an identical fingerprint). Each PDF was then posted twice to `POST /api/ingestion/static/pdf`. Per version, a fingerprint was recorded before and after each run. It covered chunk IDs, checksums, and the MD5s of the content, metadata and Gemini vector, plus a separate fingerprint over the Ollama vector, model and timestamp.

| Version | Chunks | Gemini vectors | Ollama vectors before → run 1 → run 2 | Content/metadata/Gemini fingerprint | Ollama fingerprint run 1 = run 2 |
|---|---:|---:|---|---|---|
| 8 | 5 (IDs 39–43) | 5 | 0 → 5 → 5 | unchanged across all three snapshots | yes |
| 9 | 39 (IDs 44–82) | 39 | 0 → 39 → 39 | unchanged across all three snapshots | yes |

- Both runs returned HTTP 200 with `alreadyIngested=true`: 4 pages / 5 chunks and 8 pages / 39 chunks, the same counts as the Gemini evidence.
- Run 1 filled only the NULL `embedding_ollama` values. No chunk was inserted, deleted, or had its content, metadata or Gemini vector changed, and the database total stayed at 75 chunks.
- Run 2 changed nothing, including `embedding_ollama_created_at`, so re-ingestion is idempotent under Ollama.
- All 44 Ollama vectors have 768 dimensions and unit L2 norm, are pairwise distinct, and carry model `nomic-embed-text:137m-v1.5-fp16`.

**20-chunk review under Ollama.** The text of the 20 reviewed chunks is byte-identical to the Gemini review, because the content fingerprint is unchanged. The text-level PASS results above therefore still hold. As an embedding-level check, each of the 20 chunks was queried using its own first sentence (≤ 200 characters, `search_query:` prefix), with cosine KNN over `embedding_ollama` within its version:

| Result | Count |
|---|---:|
| Chunk ranked 1st | 16 / 20 |
| Chunk in top 3 | 17 / 20 |
| Chunk ranked 4–10 | 1 (ID 40, rank 5: table-row fragment) |
| Chunk not in top 10 | 2 (ID 67: interleaved two-column table text; ID 78: reference-list entry) |

The three weak cases are chunks whose leading text is a table or reference fragment, so a first-sentence query is a poor proxy for them. This is a known extraction-layout limit, not a storage defect. No source text excerpts are included.
