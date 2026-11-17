
package com.devdocs.ai.ingeststatic.repository;

import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DocumentVersionRepository {

    private static final String FIND_BY_VERSION_AND_DOCUMENT = """
            SELECT
                dv.id AS version_id,
                dv.document_id,
                dv.version_label,
                dv.checksum,
                d.source_uri,
                d.source_type
            FROM document_versions dv
            JOIN documents d ON d.id = dv.document_id
            WHERE dv.id = ?
              AND dv.document_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DocumentVersionInfo> findByVersionIdAndDocumentId(
            Long versionId,
            Long documentId
    ) {
        List<DocumentVersionInfo> results = jdbcTemplate.query(
                FIND_BY_VERSION_AND_DOCUMENT,
                (resultSet, rowNumber) -> new DocumentVersionInfo(
                        resultSet.getLong("version_id"),
                        resultSet.getLong("document_id"),
                        resultSet.getString("version_label"),
                        resultSet.getString("checksum"),
                        resultSet.getString("source_uri"),
                        resultSet.getString("source_type")
                ),
                versionId,
                documentId
        );

        return results.stream().findFirst();
    }
}
