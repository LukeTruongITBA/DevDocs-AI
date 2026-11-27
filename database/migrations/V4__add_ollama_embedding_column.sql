-- DevDocs AI: V4 additive Ollama embedding column.
--
-- Adds a second, independent vector column for the local Ollama provider
-- (nomic-embed-text, 768 dimensions) next to the existing Gemini
-- chunks.embedding VECTOR(1536) column. The Gemini column, its HNSW index and
-- all existing rows are left untouched; no data is rewritten or backfilled.
--
-- Provenance columns record which model produced each Ollama vector and when.
-- The CHECK constraint keeps the vector and its provenance consistent: either
-- all three are NULL (not yet embedded with Ollama) or all three are set.
--
-- This migration is applied manually, like V1-V3:
--   psql --single-transaction -v ON_ERROR_STOP=1 -f V4__add_ollama_embedding_column.sql
-- Every statement is idempotent so a re-run is a no-op.

ALTER TABLE chunks
    ADD COLUMN IF NOT EXISTS embedding_ollama VECTOR(768);

ALTER TABLE chunks
    ADD COLUMN IF NOT EXISTS embedding_ollama_model VARCHAR(200);

ALTER TABLE chunks
    ADD COLUMN IF NOT EXISTS embedding_ollama_created_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_chunks_embedding_ollama_provenance'
          AND conrelid = 'chunks'::regclass
    ) THEN
        ALTER TABLE chunks
            ADD CONSTRAINT chk_chunks_embedding_ollama_provenance
            CHECK (
                (
                    embedding_ollama IS NULL
                    AND embedding_ollama_model IS NULL
                    AND embedding_ollama_created_at IS NULL
                )
                OR
                (
                    embedding_ollama IS NOT NULL
                    AND COALESCE(BTRIM(embedding_ollama_model), '') <> ''
                    AND embedding_ollama_created_at IS NOT NULL
                )
            );
    END IF;
END $$;

-- Cosine-distance HNSW index matching the existing Gemini index
-- (idx_chunks_embedding). Rows with a NULL vector are not indexed.
CREATE INDEX IF NOT EXISTS idx_chunks_embedding_ollama
ON chunks USING hnsw (embedding_ollama vector_cosine_ops);
