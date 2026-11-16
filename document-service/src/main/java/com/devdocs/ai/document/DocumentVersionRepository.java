
package com.devdocs.ai.document;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DocumentVersionRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<DocumentVersion> rowMapper =
            (rs, rowNum) -> new DocumentVersion(
                    rs.getLong("id"),
                    rs.getLong("document_id"),
                    rs.getString("version_label"),
                    rs.getString("checksum").trim(),
                    rs.getBoolean("is_current"),
                    rs.getObject(
                            "fetched_at",
                            java.time.OffsetDateTime.class
                    ),
                    rs.getObject(
                            "created_at",
                            java.time.OffsetDateTime.class
                    )
            );

    public void clearCurrentVersion(Long documentId) {
        String sql = """
                UPDATE document_versions
                SET is_current = false
                WHERE document_id = ?
                  AND is_current = true
                """;

        jdbcTemplate.update(sql, documentId);
    }

    public DocumentVersion save(
            Long documentId,
            String versionLabel,
            String checksum
    ) {
        String sql = """
                INSERT INTO document_versions
                    (document_id, version_label, checksum, is_current)
                VALUES (?, ?, ?, true)
                RETURNING id, document_id, version_label, checksum,
                          is_current, fetched_at, created_at
                """;

        return jdbcTemplate.queryForObject(
                sql,
                rowMapper,
                documentId,
                versionLabel,
                checksum
        );
    }

    public List<DocumentVersion> findByDocumentId(Long documentId) {
        String sql = """
                SELECT id, document_id, version_label, checksum,
                       is_current, fetched_at, created_at
                FROM document_versions
                WHERE document_id = ?
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(sql, rowMapper, documentId);
    }
}
