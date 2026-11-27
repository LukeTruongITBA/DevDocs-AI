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

The existing PostgreSQL `chunks.embedding VECTOR(1536)` column remains the
Gemini compatibility path. Ollama persistence and retrieval remain disabled
until the separately authorized dual-column migration adds
`chunks.embedding_ollama VECTOR(768)` and its index. Cloudflare remains the
generation provider.

For a Docker deployment, configure `api-url` to the approved Ollama endpoint
reachable from that network. No Docker or environment files are changed by
this implementation.
