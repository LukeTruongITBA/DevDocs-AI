
-- Ensure each WEB documentation source has only one document.
CREATE UNIQUE INDEX IF NOT EXISTS uq_web_document_source_uri
ON documents (source_uri)
WHERE source_type = 'WEB'
  AND source_uri IS NOT NULL;
