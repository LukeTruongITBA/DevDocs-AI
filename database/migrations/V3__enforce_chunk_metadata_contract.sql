-- Backfill the shared metadata contract from authoritative document/version
-- and chunk columns. This migration is intentionally not applied by the app.
-- Applying it to an existing database requires explicit approval.
--
-- Execute through a transactional migration runner or with psql --single-transaction.
-- The preflight rejects rows without a safe representation before mutation.
DO $$
DECLARE
    unsafe_rows BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO unsafe_rows
    FROM chunks c
    JOIN document_versions dv ON dv.id = c.version_id
    JOIN documents d ON d.id = dv.document_id
    WHERE BTRIM(dv.version_label) = ''
       OR (
            d.source_type = 'STATIC'
            AND (
                c.page_number IS NULL
                OR c.page_number <= 0
                OR COALESCE(
                    NULLIF(BTRIM(c.metadata->>'source'), ''),
                    NULLIF(BTRIM(d.source_uri), ''),
                    NULLIF(BTRIM(d.title), '')
                ) IS NULL
            )
       )
       OR (
            d.source_type = 'WEB'
            AND (
                COALESCE(
                    NULLIF(BTRIM(c.metadata->>'source_url'), ''),
                    NULLIF(BTRIM(d.source_uri), '')
                ) IS NULL
                OR COALESCE(
                    NULLIF(BTRIM(c.metadata->>'page_title'), ''),
                    NULLIF(BTRIM(d.title), '')
                ) IS NULL
            )
       );

    IF unsafe_rows > 0 THEN
        RAISE EXCEPTION
            'V3 cannot safely backfill metadata for % chunk rows; no rows were changed',
            unsafe_rows;
    END IF;
END $$;

UPDATE chunks c
SET metadata = c.metadata || jsonb_build_object(
    'doc_id', d.id,
    'version', dv.version_label,
    'source', CASE
        WHEN d.source_type = 'WEB'
            THEN COALESCE(
                NULLIF(BTRIM(c.metadata->>'source_url'), ''),
                NULLIF(BTRIM(d.source_uri), '')
            )
        ELSE COALESCE(
            NULLIF(BTRIM(c.metadata->>'source'), ''),
            NULLIF(BTRIM(d.source_uri), ''),
            NULLIF(BTRIM(d.title), '')
        )
    END,
    'source_type', d.source_type,
    'page', CASE
        WHEN d.source_type = 'WEB' THEN jsonb_build_object(
            'type', 'web',
            'url', COALESCE(
                NULLIF(BTRIM(c.metadata->>'source_url'), ''),
                NULLIF(BTRIM(d.source_uri), '')
            ),
            'title', COALESCE(
                NULLIF(BTRIM(c.metadata->>'page_title'), ''),
                NULLIF(BTRIM(d.title), '')
            )
        )
        ELSE to_jsonb(c.page_number)
    END,
    'chunk_index', c.chunk_index,
    'checksum', c.checksum
)
FROM document_versions dv
JOIN documents d ON d.id = dv.document_id
WHERE c.version_id = dv.id;

ALTER TABLE chunks
    ADD CONSTRAINT chk_chunks_metadata_contract
    CHECK ((
        jsonb_typeof(metadata) = 'object'
        AND metadata ?& ARRAY[
            'doc_id', 'version', 'source', 'source_type', 'page',
            'chunk_index', 'checksum'
        ]
        AND metadata->>'doc_id' ~ '^[1-9][0-9]*$'
        AND COALESCE(metadata->>'version', '') <> ''
        AND COALESCE(metadata->>'source', '') <> ''
        AND metadata->>'chunk_index' = chunk_index::text
        AND metadata->>'checksum' = checksum
        AND (
            (
                metadata->>'source_type' = 'STATIC'
                AND jsonb_typeof(metadata->'page') = 'number'
                AND metadata->>'page' ~ '^[1-9][0-9]*$'
                AND metadata->>'page' = page_number::text
                AND page_number > 0
            )
            OR
            (
                metadata->>'source_type' = 'WEB'
                AND page_number IS NULL
                AND jsonb_typeof(metadata->'page') = 'object'
                AND metadata->'page'->>'type' = 'web'
                AND COALESCE(metadata->'page'->>'url', '') <> ''
                AND COALESCE(metadata->'page'->>'title', '') <> ''
                AND metadata->'page'->>'url' = metadata->>'source'
            )
        )
    ) IS TRUE
    );
