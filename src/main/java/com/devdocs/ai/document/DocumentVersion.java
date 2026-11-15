
package com.devdocs.ai.document;

import java.time.OffsetDateTime;

public record DocumentVersion(
        Long id,
        Long documentId,
        String versionLabel,
        String checksum,
        boolean current,
        OffsetDateTime fetchedAt,
        OffsetDateTime createdAt
) {
}
