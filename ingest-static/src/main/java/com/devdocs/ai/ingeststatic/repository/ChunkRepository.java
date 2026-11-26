package com.devdocs.ai.ingeststatic.repository;

import com.devdocs.ai.ingeststatic.chunk.ChunkEmbedding;
import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.config.EmbeddingProperties;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class ChunkRepository {


    private static final String INSERT_CHUNK = """
            INSERT INTO chunks (
                version_id,
                chunk_index,
                content,
                page_number,
                checksum,
                metadata
            )
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
            """;

    private static final String INSERT_CHUNK_WITH_EMBEDDING = """
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

    private static final String UPDATE_MISSING_EMBEDDING = """
            UPDATE chunks
            SET embedding = CAST(? AS vector)
            WHERE version_id = ?
              AND chunk_index = ?
              AND embedding IS NULL
            """;

    private static final String COUNT_BY_VERSION = """
            SELECT COUNT(*)
            FROM chunks
            WHERE version_id = ?
            """;

    private static final String FIND_BY_VERSION = """
            SELECT
                chunk_index,
                checksum,
                embedding IS NULL AS embedding_missing
            FROM chunks
            WHERE version_id = ?
            ORDER BY chunk_index
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final int embeddingDimensions;

    public ChunkRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EmbeddingProperties embeddingProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingDimensions = embeddingProperties.dimensions();
    }

    public int insertAll(
            DocumentVersionInfo version,
            List<TextChunk> chunks,
            String source
    ) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }

        List<ChunkInsertData> insertData = chunks.stream()
                .map(chunk -> new ChunkInsertData(
                        chunk,
                        createMetadata(version, chunk, source)
                ))
                .toList();

        jdbcTemplate.batchUpdate(
                INSERT_CHUNK,
                insertData,
                insertData.size(),
                (statement, data) -> {
                    TextChunk chunk = data.chunk();

                    statement.setLong(1, version.versionId());
                    statement.setInt(2, chunk.chunkIndex());
                    statement.setString(3, chunk.content());
                    statement.setInt(4, chunk.pageNumber());
                    statement.setString(5, chunk.checksum());
                    statement.setString(6, data.metadataJson());
                }
        );

        return insertData.size();
    }

    public int insertAllWithEmbeddings(
            DocumentVersionInfo version,
            List<TextChunk> chunks,
            List<ChunkEmbedding> embeddings,
            String source
    ) {
        if (chunks == null || chunks.isEmpty()
                || embeddings == null
                || chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    "Chunks and embeddings must have the same non-zero size."
            );
        }

        Map<Integer, ChunkEmbedding> embeddingByIndex =
                embeddings.stream().collect(Collectors.toMap(
                        ChunkEmbedding::chunkIndex,
                        Function.identity()
                ));

        if (embeddingByIndex.size() != chunks.size()) {
            throw new IllegalArgumentException(
                    "Embedding indices must be unique and match all chunks."
            );
        }

        List<ChunkInsertData> insertData = chunks.stream()
                .map(chunk -> {
                    ChunkEmbedding embedding =
                            embeddingByIndex.get(chunk.chunkIndex());

                    if (embedding == null) {
                        throw new IllegalArgumentException(
                                "Missing embedding for chunk "
                                        + chunk.chunkIndex() + "."
                        );
                    }

                    return new ChunkInsertData(
                            chunk,
                            createMetadata(version, chunk, source),
                            toVectorLiteral(embedding.vector())
                    );
                })
                .toList();

        jdbcTemplate.batchUpdate(
                INSERT_CHUNK_WITH_EMBEDDING,
                insertData,
                insertData.size(),
                (statement, data) -> {
                    TextChunk chunk = data.chunk();

                    statement.setLong(1, version.versionId());
                    statement.setInt(2, chunk.chunkIndex());
                    statement.setString(3, chunk.content());
                    statement.setInt(4, chunk.pageNumber());
                    statement.setString(5, chunk.checksum());
                    statement.setString(6, data.metadataJson());
                    statement.setString(7, data.vectorLiteral());
                }
        );

        return insertData.size();
    }

    public int updateMissingEmbeddings(
            Long versionId,
            List<ChunkEmbedding> embeddings
    ) {
        if (embeddings == null || embeddings.isEmpty()) {
            return 0;
        }

        List<ChunkEmbeddingData> updateData = embeddings.stream()
                .map(embedding -> new ChunkEmbeddingData(
                        embedding,
                        toVectorLiteral(embedding.vector())
                ))
                .toList();

        int[][] results = jdbcTemplate.batchUpdate(
                UPDATE_MISSING_EMBEDDING,
                updateData,
                updateData.size(),
                (statement, data) -> {
                    statement.setString(1, data.vectorLiteral());
                    statement.setLong(2, versionId);
                    statement.setInt(3, data.embedding().chunkIndex());
                }
        );

        int updatedCount = 0;

        for (int[] batch : results) {
            for (int result : batch) {
                if (result > 0) {
                    updatedCount += result;
                }
            }
        }

        return updatedCount;
    }

    public long countByVersionId(Long versionId) {
        Long count = jdbcTemplate.queryForObject(
                COUNT_BY_VERSION,
                Long.class,
                versionId
        );

        return count == null ? 0 : count;
    }

    public List<StoredChunkInfo> findByVersionId(Long versionId) {
        return jdbcTemplate.query(
                FIND_BY_VERSION,
                (resultSet, rowNumber) -> new StoredChunkInfo(
                        resultSet.getInt("chunk_index"),
                        resultSet.getString("checksum"),
                        resultSet.getBoolean("embedding_missing")
                ),
                versionId
        );
    }

    private String toVectorLiteral(List<Double> vector) {
        if (vector == null || vector.size() != embeddingDimensions) {
            throw new IllegalArgumentException(
                    "Embedding dimensions do not match configured PostgreSQL dimensions."
            );
        }

        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Embedding contains an invalid numeric value."
                );
            }
        }

        return vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String createMetadata(
            DocumentVersionInfo version,
            TextChunk chunk,
            String source
    ) {
        try {
            return objectMapper.writeValueAsString(
                    StaticChunkMetadataFactory.create(version, chunk, source)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize chunk metadata.",
                    exception
            );
        }
    }

    private record ChunkInsertData(
            TextChunk chunk,
            String metadataJson,
            String vectorLiteral
    ) {
        private ChunkInsertData(
                TextChunk chunk,
                String metadataJson
        ) {
            this(chunk, metadataJson, null);
        }
    }

    private record ChunkEmbeddingData(
            ChunkEmbedding embedding,
            String vectorLiteral
    ) {
    }
}
