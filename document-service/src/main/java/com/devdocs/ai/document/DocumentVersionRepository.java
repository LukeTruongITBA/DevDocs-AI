
package com.devdocs.ai.document;

import org.springframework.dao.DuplicateKeyException;
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

    public DocumentVersion findByDocumentAndLabel(
            Long documentId,
            String versionLabel
    ) {
        String sql = """
                SELECT id, document_id, version_label, checksum,
                       is_current, fetched_at, created_at
                FROM document_versions
                WHERE document_id = ?
                  AND version_label = ?
                """;

        return jdbcTemplate.query(
                sql,
                rowMapper,
                documentId,
                versionLabel
        ).stream().findFirst().orElse(null);
    }

    public boolean checksumExistsForAnotherVersion(
            Long documentId,
            String checksum,
            Long excludedVersionId
    ) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM document_versions
                    WHERE document_id = ?
                      AND checksum = ?
                      AND id <> ?
                )
                """;

        Boolean exists = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                documentId,
                checksum,
                excludedVersionId
        );

        return Boolean.TRUE.equals(exists);
    }

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
                    (document_id, version_label, checksum,
                     is_current, fetched_at)
                VALUES (?, ?, ?, true, NOW())
                RETURNING id, document_id, version_label, checksum,
                          is_current, fetched_at, created_at
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    rowMapper,
                    documentId,
                    versionLabel,
                    checksum
            );
        } catch (DuplicateKeyException exception) {
            throw exception;
        }
    }

    public DocumentVersion updateChecksum(
            Long versionId,
            String checksum
    ) {
        String sql = """
                UPDATE document_versions
                SET checksum = ?,
                    fetched_at = NOW()
                WHERE id = ?
                RETURNING id, document_id, version_label, checksum,
                          is_current, fetched_at, created_at
                """;

        return jdbcTemplate.queryForObject(
                sql,
                rowMapper,
                checksum,
                versionId
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
