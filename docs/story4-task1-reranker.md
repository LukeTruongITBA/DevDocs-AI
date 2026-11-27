# Story 4 - Task 1: Cross-encoder reranker

## Acceptance criteria and evidence

| Criterion | Evidence |
|---|---|
| Retrieve a wider top-N (e.g. 20) and rerank down to top-k (e.g. 5) | `QueryPipelineService` asks the vector search for `RerankingService.candidateCount(topK)`, which is max(topK, `RERANKER_CANDIDATE_COUNT`=20) capped at 100. It reranks those candidates with the cross-encoder and returns `topK`. The response field `reranking` reports `candidates` (20) and `returned` (5). Covered by `QueryPipelineServiceTest`, which asserts that the search request uses topK 20. |
| The golden set shows a measured improvement in MRR or recall@k over no reranking | See the table below: MRR rises from 0.577 to 0.978, and recall@1 from 0.283 to 0.883. |
| The model version is pinned and added latency is documented | The model is `cross-encoder/mmarco-mMiniLMv2-L12-H384-v1` at revision `1427fd652930e4ba29e8149678df786c240d8825`, served by TEI `cpu-1.10@sha256:f0a4bb20…2d86`. Both pins are in `compose.yaml`, and TEI `/info` reports `model_sha` = the pinned revision. Latency is in the table below. |
| If the reranker fails, the pipeline keeps the original order | `RerankingService` catches every client failure and returns the vector order with `strategy=vector-order` and a `fallbackReason`, logged at WARN. Covered by unit tests (`RerankingServiceTest.keepsOriginalOrderWhenRerankerFails`, `CrossEncoderClientTest`) and a live check: with the reranker container stopped, the request still returned HTTP 200 and its top 5 equalled the vector top 5. |

## Model selection

The golden questions are Vietnamese and the documentation chunks are English, so an English-only reranker such as `ms-marco-MiniLM-L6-v2` was ruled out.

| Candidate | Params / weights | License | Languages | Decision |
|---|---|---|---|---|
| `cross-encoder/mmarco-mMiniLMv2-L12-H384-v1` | 0.1B, 471 MB (`XLMRobertaForSequenceClassification`) | Apache-2.0 | Trained on mMARCO: 14 languages, including Vietnamese | **Chosen**: smallest multilingual option, supported by TEI |
| `BAAI/bge-reranker-v2-m3` | 0.6B, 2.27 GB | Apache-2.0 | Multilingual (language list not published) | Not chosen: about 5× larger, slower on CPU |
| `cross-encoder/ms-marco-MiniLM-L6-v2` | 22.7M | Apache-2.0 | English | Not chosen: English only |

Facts were taken from the Hugging Face model and dataset cards and the TEI README on 2026-11-27. TEI's CPU backend serves the pinned revision's `onnx/model.onnx` (float32).

## Benchmark (2026-11-27)

The benchmark is reproducible with `node data/evaluation/rerank_benchmark.mjs`; the raw output is in [`data/evaluation/rerank_benchmark_2026-11-27.json`](../data/evaluation/rerank_benchmark_2026-11-27.json). It runs the 30 golden questions (`data/golden_qa`, document 5 / version 6) through the live `POST /api/query/retrieve`, using Ollama `nomic-embed-text` query embeddings and `chunks.embedding_ollama`. Both rankings use the same 11-chunk candidate set (the whole version). The baseline is the vector-similarity order (`results`), and the reranked list is `rerankedResults`.

| Metric (strict, as defined by the golden set) | Vector order | Cross-encoder | Δ |
|---|---:|---:|---:|
| MRR | 0.5767 | **0.9778** | +0.401 |
| Recall@1 | 0.2833 | **0.8833** | +0.600 |
| Recall@3 | 0.7000 | **0.9667** | +0.267 |
| Recall@5 | 0.8500 | **0.9667** | +0.117 |

The duplicate-aware variant counts chunk 18, the README's duplicate of chunk 17, as chunk 17. With it, vector MRR is 0.593 and recall@5 is 0.883; the reranked figures are unchanged. The golden version has only 11 chunks, so these numbers measure ordering quality within a small, focused corpus. They do not measure recall from a large index.

**Added latency.** Measured on this machine (12 logical CPUs, Docker Desktop). The query was topK=5 with 20 candidates of about 1,000 characters each, over MkDocs version 12 (123 chunks), with 30 samples after one warm-up:

| | p50 | p95 | max |
|---|---:|---:|---:|
| Reranker call (`reranking.latencyMs`) | 1,785 ms | 2,177 ms | 2,253 ms |
| Whole `/api/query/retrieve` | 1,940 ms | 2,310 ms | |

The TEI container used about 3.6 GB RAM. Latency scales roughly with candidate count × chunk length, so `RERANKER_CANDIDATE_COUNT` is the main tuning knob. A down reranker costs up to the connect timeout (about 4 s observed when the container was stopped) before the vector-order fallback.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `RERANKER_ENABLED` | `true` | `false` keeps vector order and makes no model call |
| `RERANKER_URL` | `http://localhost:8086` (compose: `http://reranker:80`) | TEI base URL |
| `RERANKER_CANDIDATE_COUNT` | `20` | wider top-N before reranking |
| `RERANKER_CONNECT_TIMEOUT_MILLIS` / `RERANKER_READ_TIMEOUT_MILLIS` | `2000` / `10000` | after either expires, vector order is kept |
| `RERANKER_MODEL` | pinned id@revision | label reported in responses |

The cross-encoder receives the user's original query. Normalisation and synonym expansion feed only the embedding stage.

## Baseline

- Automated tests: `RerankingServiceTest` (success, ties, failure, disabled, limits), `CrossEncoderClientTest` (score mapping, 5xx, wrong count, duplicate index, non-JSON) and `QueryPipelineServiceTest`.
- No secrets. The model is public; no Hugging Face token is needed.
- Not yet done: peer review, merge to `main`, and the Product Owner demonstration.
