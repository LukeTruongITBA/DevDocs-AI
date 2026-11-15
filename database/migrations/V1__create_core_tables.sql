
-- DevDocs AI: V1 Initial Schema

-- 1. Store original documents
CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    source_type VARCHAR(30) NOT NULL
        CHECK (source_type IN ('STATIC', 'WEB')),
    source_uri TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Store document versions
CREATE TABLE IF NOT EXISTS document_versions (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL
        REFERENCES documents(id) ON DELETE CASCADE,
    version_label VARCHAR(100) NOT NULL,
    checksum CHAR(64) NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    fetched_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_document_version
        UNIQUE (document_id, version_label),

    CONSTRAINT uq_document_checksum
        UNIQUE (document_id, checksum)
);

-- Only one current version per document
CREATE UNIQUE INDEX IF NOT EXISTS uq_one_current_version
ON document_versions(document_id)
WHERE is_current = TRUE;

-- 3. Store text chunks and embeddings
CREATE TABLE IF NOT EXISTS chunks (
    id BIGSERIAL PRIMARY KEY,
    version_id BIGINT NOT NULL
        REFERENCES document_versions(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL,
    page_number INTEGER,
    checksum CHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding VECTOR(1536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_version_chunk_index
        UNIQUE (version_id, chunk_index)
);

-- Lookup indexes
CREATE INDEX IF NOT EXISTS idx_versions_document_id
ON document_versions(document_id);

CREATE INDEX IF NOT EXISTS idx_chunks_version_id
ON chunks(version_id);

-- Vector similarity index
CREATE INDEX IF NOT EXISTS idx_chunks_embedding
ON chunks USING hnsw (embedding vector_cosine_ops);
