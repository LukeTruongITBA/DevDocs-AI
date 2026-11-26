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
