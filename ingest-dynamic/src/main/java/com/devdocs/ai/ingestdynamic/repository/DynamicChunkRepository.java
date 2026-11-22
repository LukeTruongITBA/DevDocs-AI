
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DynamicChunkRepository {

    private static final String DELETE_SQL = """
            DELETE FROM chunks
            WHERE version_id = ?
            """;

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

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final int embeddingDimensions;

    public DynamicChunkRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EmbeddingProperties embeddingProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingDimensions = embeddingProperties.dimensions();
    }

    /**
     * Replaces all chunks belonging to a document version.
     *
     * Chunk indexes are reassigned globally across all crawled pages.
     * The delete and insert operations run in one database transaction.
     */
    @Transactional
    public void replaceChunks(
            Long documentId,
            Long versionId,
            List<TextChunk> chunks,
            List<float[]> embeddings
    ) {
        validateInput(documentId, versionId, chunks, embeddings);

        jdbcTemplate.update(DELETE_SQL, versionId);

        jdbcTemplate.batchUpdate(
                INSERT_SQL,
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
                        statement.setNull(4, Types.INTEGER);
                        statement.setString(5, checksum);
                        statement.setString(6, metadata);
                        statement.setString(
                                7,
                                toVectorLiteral(embedding)
                        );
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                }
        );
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
    }

    private String createMetadata(
            Long documentId,
            TextChunk chunk,
            int globalChunkIndex,
            String checksum
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put("doc_id", documentId);
        metadata.put("version", chunk.version());
        metadata.put("source", "WEB");
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
