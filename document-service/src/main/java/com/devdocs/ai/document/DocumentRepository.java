
package com.devdocs.ai.document;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Document> documentRowMapper =
            (rs, rowNum) -> new Document(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("source_type"),
                    rs.getString("source_uri"),
                    rs.getObject(
                            "created_at",
                            java.time.OffsetDateTime.class
                    ),
                    rs.getObject(
                            "updated_at",
                            java.time.OffsetDateTime.class
                    )
            );

    public Document save(
            String title,
            String sourceType,
            String sourceUri
    ) {
        String sql = """
                INSERT INTO documents
                    (title, source_type, source_uri)
                VALUES (?, ?, ?)
                RETURNING id, title, source_type, source_uri,
                          created_at, updated_at
                """;

        return jdbcTemplate.queryForObject(
                sql,
                documentRowMapper,
                title,
                sourceType,
                sourceUri
        );
    }


    public Document saveOrGetWebDocument(
            String title,
            String sourceUri
    ) {
        String sql = """
                INSERT INTO documents
                    (title, source_type, source_uri)
                VALUES (?, 'WEB', ?)
                ON CONFLICT (source_uri)
                    WHERE source_type = 'WEB'
                      AND source_uri IS NOT NULL
                DO UPDATE SET
                    title = EXCLUDED.title,
                    updated_at = NOW()
                RETURNING id, title, source_type, source_uri,
                          created_at, updated_at
                """;

        return jdbcTemplate.queryForObject(
                sql,
                documentRowMapper,
                title,
                sourceUri
        );
    }


    public List<Document> findAll() {
        String sql = """
                SELECT id, title, source_type, source_uri,
                       created_at, updated_at
                FROM documents
                ORDER BY id DESC
                """;

        return jdbcTemplate.query(sql, documentRowMapper);
    }

    /** Uses the partial unique index uq_web_document_source_uri (V2): at most one row. */
    public List<Document> findWebBySourceUri(String sourceUri) {
        String sql = """
                SELECT id, title, source_type, source_uri,
                       created_at, updated_at
                FROM documents
                WHERE source_type = 'WEB'
                  AND source_uri = ?
                """;

        return jdbcTemplate.query(sql, documentRowMapper, sourceUri);
    }

    public Document findById(Long id) {
        String sql = """
                SELECT id, title, source_type, source_uri,
                       created_at, updated_at
                FROM documents
                WHERE id = ?
                """;

        return jdbcTemplate.queryForObject(
                sql,
                documentRowMapper,
                id
        );
    }
}
