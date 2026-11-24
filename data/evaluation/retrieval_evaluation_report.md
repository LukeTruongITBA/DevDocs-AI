# Retrieval Evaluation Report

## 1. Evaluation Overview

- **Project:** DevDocs AI
- **Evaluation date:** 24 November 2026
- **Dataset:** Spring Boot 4.2.0 Golden QA
- **Dataset size:** 30 questions
- **Version ID:** 6
- **Top K:** 10
- **Successful requests:** 30/30
- **Failed requests:** 0/30

The evaluation compares retrieval rankings before and after the Reranking stage.

## 2. Evaluation Metrics

- **Recall@5:** Number of relevant chunks found in the first 5 results divided by the total number of relevant chunks.
- **Recall@10:** Number of relevant chunks found in the first 10 results divided by the total number of relevant chunks.
- **MRR (Mean Reciprocal Rank):** Average reciprocal rank of the first relevant result across all questions. If no relevant result is found, the reciprocal rank is zero.

The relevance comparison uses chunk IDs from
elevant_chunk_ids and result IDs returned by the API.

## 3. Aggregate Results

| Method | Recall@5 | Recall@10 | MRR |
|---|---:|---:|---:|
| Vector Search | 100.00 % | 100.00 % | 0.8556 |
| After Reranking | 100.00 % | 100.00 % | 0.9500 |

## 4. Per-question Results

| Question ID | Vector Rank | Reranked Rank | Vector Recall@5 | Reranked Recall@5 | Vector MRR | Reranked MRR |
|---|---:|---:|---:|---:|---:|---:|
| SB42-001 | 2 | 1 | 100.00 % | 100.00 % | 0.5000 | 1.0000 |
| SB42-002 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-003 | 2 | 1 | 100.00 % | 100.00 % | 0.5000 | 1.0000 |
| SB42-004 | 2 | 1 | 100.00 % | 100.00 % | 0.5000 | 1.0000 |
| SB42-005 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-006 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-007 | 2 | 1 | 100.00 % | 100.00 % | 0.5000 | 1.0000 |
| SB42-008 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-009 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-010 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-011 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-012 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-013 | 3 | 1 | 100.00 % | 100.00 % | 0.3333 | 1.0000 |
| SB42-014 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-015 | 3 | 2 | 100.00 % | 100.00 % | 0.3333 | 0.5000 |
| SB42-016 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-017 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-018 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-019 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-020 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-021 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-022 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-023 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-024 | 2 | 2 | 100.00 % | 100.00 % | 0.5000 | 0.5000 |
| SB42-025 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-026 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-027 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-028 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |
| SB42-029 | 2 | 2 | 100.00 % | 100.00 % | 0.5000 | 0.5000 |
| SB42-030 | 1 | 1 | 100.00 % | 100.00 % | 1.0000 | 1.0000 |

## 5. Analysis

- Recall@5 remained at 100% before and after Reranking.
- Recall@10 remained at 100% before and after Reranking.
- MRR increased from 0.8556 to 0.9500.
- Absolute MRR improvement: 0.0944 points.
- Relative MRR improvement: 11.04%.

These results indicate that Reranking improved the ordering of relevant results on this evaluation dataset, while maintaining the measured Recall@5 and Recall@10.

## 6. Limitations

- The evaluation covers 30 questions from the Spring Boot 4.2.0 Golden QA dataset only.
- The questions cover the chunks represented in the supplied dataset, not the entire Spring Boot documentation.
- The results do not establish performance on other documentation versions or Java/Spring projects.
- The evaluation measures retrieval quality, not the factual correctness or quality of generated answers.

## 7. Conclusion

On this 30-question dataset, the retrieval pipeline achieved 100% Recall@5 and Recall@10. Reranking improved MRR from 0.8556 to 0.9500, indicating better ranking of relevant chunks.
