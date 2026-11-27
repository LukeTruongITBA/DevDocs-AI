package com.devdocs.ai.preretrieval.vectorsearch.repository;

import com.devdocs.ai.preretrieval.filter.MetadataFilter;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class VectorSearchRepository {

    /**
     * {@code %1$s} is replaced only by an {@link EmbeddingColumn} constant and
     * {@code %2$s} only by constant predicates built in {@link #buildSql}
     * (filter fragments come from whitelisted enums with {@code ?} placeholders).
     */
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
        WHERE c.%1$s IS NOT NULL%2$s
        ORDER BY c.%1$s <=> q.embedding
        LIMIT ?
        """;

    /**
     * pgvector 0.8 iterative HNSW scans keep searching the index until
     * enough rows pass the WHERE clause, so selective filters still return
     * up to topK rows instead of an under-filled post-filtered page.
     */
    private static final String ENABLE_ITERATIVE_SCAN =
            "SET LOCAL hnsw.iterative_scan = strict_order";

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
        return search(versionId, embedding, topK, null);
    }

    /**
     * @param versionId optional version scope; null searches all versions
     *                  (only allowed by the service when a filter is given)
     * @param filter    optional validated metadata filter; null = standard flow
     */
    @Transactional(readOnly = true)
    public List<VectorSearchResult> search(
            Long versionId,
            List<Double> embedding,
            Integer topK,
            MetadataFilter filter
    ) {
        EmbeddingColumn column = EmbeddingColumn.forDimensions(embedding.size())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No embedding column stores " + embedding.size() + " dimensions"));

        String vectorValue = embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));

        List<Object> parameters = new ArrayList<>();
        parameters.add(vectorValue);
        String sql = buildSql(column, versionId, filter, parameters);
        parameters.add(topK);

        if (filter != null) {
            jdbcTemplate.execute(ENABLE_ITERATIVE_SCAN);
        }

        return jdbcTemplate.query(
                sql,
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
                parameters.toArray()
        );
    }

    /** Package-visible for SQL-shape tests. Appends bound values to {@code parameters}. */
    static String buildSql(
            EmbeddingColumn column,
            Long versionId,
            MetadataFilter filter,
            List<Object> parameters
    ) {
        StringBuilder predicates = new StringBuilder();
        if (versionId != null) {
            predicates.append("\n          AND c.version_id = ?");
            parameters.add(versionId);
        }
        if (filter != null) {
            predicates.append("\n          AND ").append(filter.toSql(parameters));
        }
        return SEARCH_SQL_TEMPLATE.formatted(column.columnName(), predicates);
    }
}
