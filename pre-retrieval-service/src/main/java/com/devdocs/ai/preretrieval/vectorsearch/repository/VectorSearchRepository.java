
package com.devdocs.ai.preretrieval.vectorsearch.repository;

import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class VectorSearchRepository {

    /** {@code %1$s} is replaced only by an {@link EmbeddingColumn} constant. */
    private static final String SEARCH_SQL_TEMPLATE = """
        WITH query_vector AS (
            SELECT CAST(? AS vector) AS embedding
        )
        SELECT
            c.id,
            c.version_id,
            d.id AS document_id,
            d.title AS document_title,
            dv.version_label,
            d.source_uri,
            c.chunk_index,
            c.content,
            c.page_number,
            c.metadata::text AS metadata,
            1 - (c.%1$s <=> q.embedding) AS similarity
        FROM chunks c
        JOIN document_versions dv
            ON dv.id = c.version_id
        JOIN documents d
            ON d.id = dv.document_id
        CROSS JOIN query_vector q
        WHERE c.version_id = ?
          AND c.%1$s IS NOT NULL
        ORDER BY c.%1$s <=> q.embedding
        LIMIT ?
        """;

    private static final java.util.Map<EmbeddingColumn, String> SEARCH_SQL =
            java.util.Arrays.stream(EmbeddingColumn.values())
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            column -> column,
                            column -> SEARCH_SQL_TEMPLATE.formatted(column.columnName())
                    ));

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public VectorSearchRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<VectorSearchResult> search(
            Long versionId,
            List<Double> embedding,
            Integer topK
    ) {
        EmbeddingColumn column = EmbeddingColumn.forDimensions(embedding.size())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No embedding column stores " + embedding.size() + " dimensions"));

        String vectorValue = embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));

        return jdbcTemplate.query(
                SEARCH_SQL.get(column),
                (rs, rowNum) -> {
                    Map<String, Object> metadata;

                    try {
                        metadata = objectMapper.readValue(
                                rs.getString("metadata"),
                                new TypeReference<Map<String, Object>>() {}
                        );
                    } catch (JacksonException exception) {
                        throw new SQLException(
                                "Failed to parse chunk metadata JSON",
                                exception
                        );
                    }

                    return new VectorSearchResult(
                            rs.getLong("id"),
                            rs.getLong("version_id"),
                            rs.getLong("document_id"),
                            rs.getString("document_title"),
                            rs.getString("version_label"),
                            rs.getString("source_uri"),
                            rs.getInt("chunk_index"),
                            rs.getString("content"),
                            rs.getObject("page_number", Integer.class),
                            metadata,
                            rs.getDouble("similarity")
                    );
                },
                vectorValue,
                versionId,
                topK
        );
    }
}
