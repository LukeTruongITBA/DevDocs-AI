// Story 4 Task 1 benchmark: cross-encoder reranking vs. vector order on the
// golden QA set, through the live pre-retrieval pipeline.
//
//   node data/evaluation/rerank_benchmark.mjs [baseUrl] [latencyVersionId]
//
// Quality: each golden question is sent to POST /api/query/retrieve with the
// golden versionId and topK = number of chunks in that version, so both
// rankings cover the same full candidate set. "results" is the vector order
// (baseline), "rerankedResults" is the cross-encoder order.
// Latency: the reranker's own latencyMs at topK=5 / 20 candidates on a larger
// version (default 12, MkDocs user guide, 123 chunks), repeated over all
// golden questions after one warm-up call.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const baseUrl = process.argv[2] ?? 'http://localhost:8084';
const latencyVersionId = Number(process.argv[3] ?? 12);
const golden = readFileSync(fileURLToPath(new URL('../golden_qa/spring_boot_4_2_golden_qa.jsonl', import.meta.url)), 'utf8')
  .split('\n').filter((l) => l.trim()).map((l) => JSON.parse(l));

// The golden README marks chunk 18 as duplicate content of canonical chunk 17.
const DUPLICATES = { 18: 17 };
const canon = (id) => DUPLICATES[id] ?? id;

const retrieve = async (query, versionId, topK) => {
  const res = await fetch(`${baseUrl}/api/query/retrieve`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ query, versionId, topK }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} for ${query}`);
  return res.json();
};

const metrics = (rankings, relevantSets, dedup) => {
  const ks = [1, 3, 5];
  const recall = Object.fromEntries(ks.map((k) => [k, 0]));
  let mrr = 0;
  rankings.forEach((ids, q) => {
    const rel = new Set(relevantSets[q]);
    let seen = ids.map((id) => (dedup ? canon(id) : id));
    if (dedup) seen = seen.filter((id, i) => seen.indexOf(id) === i);
    const firstHit = seen.findIndex((id) => rel.has(id));
    if (firstHit >= 0) mrr += 1 / (firstHit + 1);
    for (const k of ks) {
      const hits = seen.slice(0, k).filter((id) => rel.has(id)).length;
      recall[k] += hits / rel.size;
    }
  });
  const n = rankings.length;
  return {
    mrr: +(mrr / n).toFixed(4),
    ...Object.fromEntries(ks.map((k) => [`recall@${k}`, +(recall[k] / n).toFixed(4)])),
  };
};

// ---- quality
const versionId = golden[0].version_id;
const probe = await retrieve(golden[0].question, versionId, 100);
const corpus = probe.results.length;
const vectorRankings = [];
const rerankRankings = [];
const strategies = new Set();
for (const item of golden) {
  const r = await retrieve(item.question, versionId, corpus);
  strategies.add(r.reranking.strategy);
  vectorRankings.push(r.results.map((x) => x.id));
  rerankRankings.push(r.rerankedResults.map((x) => x.chunk.id));
}
const relevant = golden.map((g) => g.relevant_chunk_ids);
const quality = {
  questions: golden.length,
  versionId,
  candidatesPerQuery: corpus,
  strategies: [...strategies],
  strict: { vector: metrics(vectorRankings, relevant, false), reranked: metrics(rerankRankings, relevant, false) },
  duplicateAware: { vector: metrics(vectorRankings, relevant, true), reranked: metrics(rerankRankings, relevant, true) },
};

// ---- latency (reranker call only, and whole retrieve call)
await retrieve(golden[0].question, latencyVersionId, 5); // warm-up
const rerankMs = [];
const totalMs = [];
let candidates = 0;
for (const item of golden) {
  const t0 = performance.now();
  const r = await retrieve(item.question, latencyVersionId, 5);
  totalMs.push(performance.now() - t0);
  rerankMs.push(r.reranking.latencyMs);
  candidates = r.reranking.candidates;
}
const pct = (xs, p) => { const s = [...xs].sort((a, b) => a - b); return Math.round(s[Math.min(s.length - 1, Math.floor(p * s.length))]); };
const latency = {
  versionId: latencyVersionId, candidates, topK: 5, samples: rerankMs.length,
  rerankerMs: { p50: pct(rerankMs, 0.5), p95: pct(rerankMs, 0.95), max: Math.max(...rerankMs) },
  retrieveTotalMs: { p50: pct(totalMs, 0.5), p95: pct(totalMs, 0.95) },
  model: probe.reranking.model,
};

console.log(JSON.stringify({ quality, latency }, null, 2));
