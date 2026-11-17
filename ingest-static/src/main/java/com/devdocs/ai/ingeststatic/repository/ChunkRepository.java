package com.devdocs.ai.ingeststatic.repository;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;
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

    private static final String COUNT_BY_VERSION = """
            SELECT COUNT(*)
            FROM chunks
            WHERE version_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChunkRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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

        int[][] results = jdbcTemplate.batchUpdate(
                INSERT_CHUNK,
                insertData,
                insertData.size(),
                new ParameterizedPreparedStatementSetter<>() {
                    @Override
                    public void setValues(
                            PreparedStatement statement,
                            ChunkInsertData data
                    ) throws SQLException {
                        TextChunk chunk = data.chunk();

                        statement.setLong(1, version.versionId());
                        statement.setInt(2, chunk.chunkIndex());
                        statement.setString(3, chunk.content());
                        statement.setInt(4, chunk.pageNumber());
                        statement.setString(5, chunk.checksum());
                        statement.setString(6, data.metadataJson());
                    }
                }
        );

        return insertData.size();
    }

    public long countByVersionId(Long versionId) {
        Long count = jdbcTemplate.queryForObject(
                COUNT_BY_VERSION,
                Long.class,
                versionId
        );

        return count == null ? 0 : count;
    }

    private String createMetadata(
            DocumentVersionInfo version,
            TextChunk chunk,
            String source
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("doc_id", version.documentId());
        metadata.put("version", version.versionLabel());
        metadata.put("source", source);
        metadata.put("page", chunk.pageNumber());
        metadata.put("chunk_index", chunk.chunkIndex());
        metadata.put("checksum", chunk.checksum());

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize chunk metadata.",
                    exception
            );
        }
    }

    private record ChunkInsertData(
            TextChunk chunk,
            String metadataJson
    ) {
    }
}