# Local Ollama embedding

The three embedding pipelines can use the local Ollama provider with:

```yaml
app:
  embedding:
    provider: ollama
    api-url: http://localhost:11434/api/embed
    model: nomic-embed-text:137m-v1.5-fp16
    dimensions: 768
```

Document inputs receive `search_document: ` and query inputs receive
`search_query: ` exactly once. Responses are validated for count, 768
dimensions, numeric values, and finite values. HTTP failures, malformed
responses, unavailable Ollama, and invalid vectors fail closed; Gemini is not
used as an automatic fallback.

## Storage: dual vector columns (migration V4)

[`database/migrations/V4__add_ollama_embedding_column.sql`](../database/migrations/V4__add_ollama_embedding_column.sql)
is additive:

| Column | Type | Written by |
|---|---|---|
| `chunks.embedding` | `VECTOR(1536)` | Gemini (unchanged, HNSW cosine `idx_chunks_embedding`) |
| `chunks.embedding_ollama` | `VECTOR(768)` | Ollama (HNSW cosine `idx_chunks_embedding_ollama`) |
| `chunks.embedding_ollama_model` | `VARCHAR(200)` | model tag that produced the Ollama vector |
| `chunks.embedding_ollama_created_at` | `TIMESTAMPTZ` | when the Ollama vector was written |

`chk_chunks_embedding_ollama_provenance` requires the three Ollama columns to
be all NULL or all set. The Gemini column and its rows are never modified.

Apply manually, like V1-V3, after a verified backup:

```powershell
docker exec devdocs-postgres pg_dump -U devdocs -d devdocs -Fc > backup.dump
Get-Content database\migrations\V4__add_ollama_embedding_column.sql | docker exec -i devdocs-postgres psql -U devdocs -d devdocs --single-transaction -v ON_ERROR_STOP=1
```

Every statement is idempotent; a second run is a no-op.

Service behaviour with `provider: ollama`:

- **ingest-static / ingest-dynamic** insert new chunks into `embedding_ollama`
  with model provenance. When a source is re-ingested with identical content
  (same version checksum and chunk checksums) but its Ollama vectors are NULL,
  only those NULL vectors are filled (`... WHERE embedding_ollama IS NULL`).
  Content, metadata and Gemini vectors are never rewritten; any content
  mismatch is still a conflict.
- **pre-retrieval-service** routes a 768-dimension query vector to
  `embedding_ollama` and a 1536-dimension vector to `embedding`. Column names
  come only from the `EmbeddingColumn` enum, never from request input.

Cloudflare remains the generation provider.

## Docker

`compose.yaml` runs the three embedding services with `EMBEDDING_PROVIDER=ollama`
and reaches the host's Ollama at `http://host.docker.internal:11434/api/embed`.
It uses dedicated `OLLAMA_EMBEDDING_*` variables so a legacy Gemini
`EMBEDDING_MODEL`/`EMBEDDING_DIMENSIONS` in `.env` cannot override the 768-dim
configuration. To use Gemini outside compose, set `EMBEDDING_PROVIDER=gemini`,
`EMBEDDING_API_URL`, `EMBEDDING_MODEL=models/gemini-embedding-001` and
`EMBEDDING_DIMENSIONS=1536`.
