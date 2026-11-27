
package com.devdocs.ai.ingestdynamic.repository;

import com.devdocs.ai.ingestdynamic.config.EmbeddingProperties;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import com.devdocs.ai.ingestdynamic.util.ChecksumUtils;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

@Repository
public class DynamicChunkRepository {

    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");

    private static final String INSERT_SQL = """
            INSERT INTO chunks (
                version_id,
                chunk_index,
                content,
                page_number,
                checksum,
                metadata,
                embedding
            )
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS vector))
            """;

    /** V4: Ollama vectors go to embedding_ollama with model provenance. */
    private static final String INSERT_OLLAMA_SQL = """
            INSERT INTO chunks (
                version_id,
                chunk_index,
                content,
                page_number,
                checksum,
                metadata,
                embedding_ollama,
                embedding_ollama_model,
                embedding_ollama_created_at
            )
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS vector), ?, NOW())
            """;

    private static final String FINGERPRINT_SQL = """
            SELECT chunk_index, checksum, embedding IS NOT NULL AS has_embedding
            FROM chunks
            WHERE version_id = ?
            ORDER BY chunk_index
            """;

    private static final String FINGERPRINT_OLLAMA_SQL = """
            SELECT chunk_index, checksum, embedding_ollama IS NOT NULL AS has_embedding
            FROM chunks
            WHERE version_id = ?
            ORDER BY chunk_index
            """;

    /** Fills only a missing Ollama vector; content and Gemini data are untouched. */
    private static final String FILL_MISSING_OLLAMA_SQL = """
            UPDATE chunks
            SET embedding_ollama = CAST(? AS vector),
                embedding_ollama_model = ?,
                embedding_ollama_created_at = NOW()
            WHERE version_id = ?
              AND chunk_index = ?
              AND embedding_ollama IS NULL
            """;

    private static final String FILL_MISSING_GEMINI_SQL = """
            UPDATE chunks
            SET embedding = CAST(? AS vector)
            WHERE version_id = ?
              AND chunk_index = ?
              AND embedding IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final int embeddingDimensions;
    private final boolean ollama;
    private final String embeddingModel;

    public DynamicChunkRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EmbeddingProperties embeddingProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingDimensions = embeddingProperties.dimensions();
        this.ollama = embeddingProperties.isOllama();
        this.embeddingModel = embeddingProperties.model();
    }

    /**
     * Inserts chunks for a newly-created document version.
     *
     * Chunk indexes are reassigned globally across all crawled pages.
     * The insert operations run in one database transaction. Existing
     * versions are rejected by the service before this method is called.
     */
    @Transactional
    public void insertChunks(
            Long documentId,
            Long versionId,
            List<TextChunk> chunks,
            List<float[]> embeddings
    ) {
        validateInput(documentId, versionId, chunks, embeddings);

        jdbcTemplate.batchUpdate(
                ollama ? INSERT_OLLAMA_SQL : INSERT_SQL,
                new BatchPreparedStatementSetter() {

                    @Override
                    public void setValues(
                            PreparedStatement statement,
                            int index
                    ) throws SQLException {

                        TextChunk chunk = chunks.get(index);
                        float[] embedding = embeddings.get(index);

                        String checksum = ChecksumUtils.sha256(
                                chunk.content()
                        );

                        String metadata = createMetadata(
                                documentId,
                                chunk,
                                index,
                                checksum
                        );

                        statement.setLong(1, versionId);
                        statement.setInt(2, index);
                        statement.setString(3, chunk.content());
                        // V3 keeps dynamic resources as source_type=WEB and
                        // requires page_number NULL. PDF page provenance is
                        // stored explicitly in the JSON page metadata below.
                        statement.setNull(4, Types.INTEGER);
                        statement.setString(5, checksum);
                        statement.setString(6, metadata);
                        statement.setString(
                                7,
                                toVectorLiteral(embedding)
                        );
                        if (ollama) {
                            statement.setString(8, embeddingModel);
                        }
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                }
        );
    }

    /**
     * Checks that a version has the expected contiguous chunk indexes and
     * content checksums. Used to distinguish a completed ingestion from a
     * version row left behind by a failed chunk replacement.
     */
    public boolean hasMatchingChunks(
            Long versionId,
            List<TextChunk> expectedChunks
    ) {
        return compareStoredChunks(versionId, expectedChunks)
                == StoredChunkState.COMPLETE;
    }

    /**
     * Compares stored chunk content with the expected snapshot and reports
     * whether the configured provider's embedding column is fully populated.
     * Content mismatches are never repairable here.
     */
    public StoredChunkState compareStoredChunks(
            Long versionId,
            List<TextChunk> expectedChunks
    ) {
        if (versionId == null || versionId <= 0
                || expectedChunks == null || expectedChunks.isEmpty()) {
            return StoredChunkState.MISMATCH;
        }

        List<StoredChunkFingerprint> stored = loadFingerprints(versionId);

        if (stored.size() != expectedChunks.size()) {
            return StoredChunkState.MISMATCH;
        }

        boolean allEmbedded = true;
        for (int index = 0; index < expectedChunks.size(); index++) {
            StoredChunkFingerprint fingerprint = stored.get(index);
            String expectedChecksum = ChecksumUtils.sha256(
                    expectedChunks.get(index).content()
            );
            if (fingerprint.chunkIndex() != index
                    || !expectedChecksum.equalsIgnoreCase(
                            fingerprint.checksum()
                    )) {
                return StoredChunkState.MISMATCH;
            }
            allEmbedded &= fingerprint.hasEmbedding();
        }

        return allEmbedded
                ? StoredChunkState.COMPLETE
                : StoredChunkState.CONTENT_MATCHES_EMBEDDINGS_MISSING;
    }

    /** Chunk indexes whose configured-provider embedding is still NULL. */
    public List<Integer> findMissingEmbeddingIndexes(Long versionId) {
        return loadFingerprints(versionId).stream()
                .filter(fingerprint -> !fingerprint.hasEmbedding())
                .map(StoredChunkFingerprint::chunkIndex)
                .toList();
    }

    /**
     * Fills NULL embeddings for already-stored chunks of the configured
     * provider. Only rows whose embedding is still NULL are updated, so a
     * concurrent fill or an existing vector is never overwritten.
     *
     * @return number of rows updated
     */
    @Transactional
    public int fillMissingEmbeddings(
            Long versionId,
            List<Integer> chunkIndexes,
            List<float[]> embeddings
    ) {
        if (versionId == null || versionId <= 0
                || chunkIndexes == null || chunkIndexes.isEmpty()
                || embeddings == null
                || embeddings.size() != chunkIndexes.size()) {
            throw new IllegalArgumentException(
                    "Chunk indexes and embeddings must have the same non-zero size."
            );
        }

        for (float[] embedding : embeddings) {
            if (embedding == null || embedding.length != embeddingDimensions) {
                throw new IllegalArgumentException(
                        "Embedding dimensions do not match configuration."
                );
            }
            for (float value : embedding) {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "Embedding values must be finite."
                    );
                }
            }
        }

        int updated = 0;
        for (int i = 0; i < chunkIndexes.size(); i++) {
            String vector = toVectorLiteral(embeddings.get(i));
            updated += ollama
                    ? jdbcTemplate.update(FILL_MISSING_OLLAMA_SQL,
                            vector, embeddingModel, versionId, chunkIndexes.get(i))
                    : jdbcTemplate.update(FILL_MISSING_GEMINI_SQL,
                            vector, versionId, chunkIndexes.get(i));
        }
        return updated;
    }

    private List<StoredChunkFingerprint> loadFingerprints(Long versionId) {
        return jdbcTemplate.query(
                ollama ? FINGERPRINT_OLLAMA_SQL : FINGERPRINT_SQL,
                (resultSet, rowNum) -> new StoredChunkFingerprint(
                        resultSet.getInt("chunk_index"),
                        resultSet.getString("checksum"),
                        resultSet.getBoolean("has_embedding")
                ),
                versionId
        );
    }

    public enum StoredChunkState {
        COMPLETE,
        CONTENT_MATCHES_EMBEDDINGS_MISSING,
        MISMATCH
    }

    private record StoredChunkFingerprint(
            int chunkIndex,
            String checksum,
            boolean hasEmbedding
    ) {
    }

    private void validateInput(
            Long documentId,
            Long versionId,
            List<TextChunk> chunks,
            List<float[]> embeddings
    ) {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException(
                    "Document ID must be greater than zero."
            );
        }

        if (versionId == null || versionId <= 0) {
            throw new IllegalArgumentException(
                    "Version ID must be greater than zero."
            );
        }

        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chunks must not be null or empty."
            );
        }

        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalArgumentException(
                    "The number of embeddings must match the number of chunks."
            );
        }

        for (float[] embedding : embeddings) {
            if (embedding == null
                    || embedding.length != embeddingDimensions) {
                throw new IllegalArgumentException(
                        "Embedding dimensions do not match configuration."
                );
            }

            for (float value : embedding) {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "Embedding values must be finite."
                    );
                }
            }
        }

        for (TextChunk chunk : chunks) {
            if (chunk == null || chunk.content().isBlank()
                    || chunk.sourceUrl().isBlank() || chunk.pageTitle().isBlank()
                    || chunk.version().isBlank()) {
                throw new IllegalArgumentException(
                        "Web chunk metadata is missing a required value."
                );
            }
        }
    }

    private String createMetadata(
            Long documentId,
            TextChunk chunk,
            int globalChunkIndex,
            String checksum
    ) {
        validateMetadata(documentId, chunk, globalChunkIndex, checksum);
        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put("doc_id", documentId);
        metadata.put("version", chunk.version());
        metadata.put("source", chunk.sourceUrl());
        metadata.put("source_type", "WEB");
        metadata.put("fetch_date", chunk.fetchedAt().toString());
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("type", "web");
        page.put("url", chunk.sourceUrl());
        page.put("title", chunk.pageTitle());
        page.put("fetch_date", chunk.fetchedAt().toString());
        if (chunk.pdfPageNumber() != null) {
            page.put("pdf_page_number", chunk.pdfPageNumber());
            page.put("content_type", "application/pdf");
        }
        metadata.put("page", page);
        metadata.put("source_url", chunk.sourceUrl());
        metadata.put("page_title", chunk.pageTitle());
        metadata.put("chunk_index", globalChunkIndex);

        // Preserve the original index assigned by the page chunker.
        metadata.put("page_chunk_index", chunk.chunkIndex());

        metadata.put("checksum", checksum);

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize chunk metadata.",
                    exception
            );
        }
    }

    private void validateMetadata(
            Long documentId,
            TextChunk chunk,
            int chunkIndex,
            String checksum
    ) {
        if (documentId == null || documentId <= 0
                || chunk == null
                || chunk.version() == null || chunk.version().isBlank()
                || chunk.sourceUrl() == null || chunk.sourceUrl().isBlank()
                || chunk.pageTitle() == null || chunk.pageTitle().isBlank()
                || chunkIndex < 0
                || checksum == null || !SHA256.matcher(checksum).matches()) {
            throw new IllegalArgumentException(
                    "Web chunk metadata is missing a required value."
            );
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder vector = new StringBuilder("[");

        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                vector.append(",");
            }

            vector.append(Float.toString(embedding[i]));
        }

        return vector.append("]").toString();
    }
}
