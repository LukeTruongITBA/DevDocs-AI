# Golden QA Set — Spring Boot 4.2.0

## Scope
- Document: Spring Boot (`document_id=5`)
- Version: `4.2.0` (`version_id=6`, current at the time of dataset inspection)
- Source chunks: `17–27` (11 rows)
- Questions: 30

## Files
- `spring_boot_4_2_golden_qa.jsonl`: one JSON object per line.

## JSONL fields
- `id`: stable QA identifier.
- `question`: question to send to the RAG system.
- `reference_answer`: grounded reference answer.
- `relevant_chunk_ids`: chunk IDs expected to support the answer; use these as relevant documents for Recall@k / MRR.
- `document_id`, `version_id`, `version_label`: source document/version identity.
- `category`, `difficulty`: evaluation metadata.
- `evidence`: short supporting phrase from the supplied chunk content.

## Data-quality notes
- Chunks 17 and 18 were reported as duplicate content. The set uses chunk 17 as the canonical evidence chunk and does not count chunk 18 as a separate relevant result.
- The questions are limited to the content supplied from chunks 17–27. They do not claim coverage of the entire Spring Boot reference documentation.
- For multi-chunk answers, `relevant_chunk_ids` includes each chunk needed to support the answer.
- Review the live document/version state before reusing this set after a re-ingestion or version change.

## Validation
- 30 unique IDs and 30 questions.
- Every `relevant_chunk_ids` entry points to a chunk ID in the supplied range.
- Every item is tied to document 5 / version 6 / label 4.2.0.
