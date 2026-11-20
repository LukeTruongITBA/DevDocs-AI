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

    private static final String SEARCH_SQL = """
            WITH query_vector AS (
                SELECT CAST(? AS vector) AS embedding
            )
            SELECT
                c.id,
                c.version_id,
                c.chunk_index,
                c.content,
                c.page_number,
                c.metadata::text AS metadata,
                1 - (c.embedding <=> q.embedding) AS similarity
            FROM chunks c
            CROSS JOIN query_vector q
            WHERE c.version_id = ?
              AND c.embedding IS NOT NULL
            ORDER BY c.embedding <=> q.embedding
            LIMIT ?
            """;

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
        String vectorValue = embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));

        return jdbcTemplate.query(
                SEARCH_SQL,
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