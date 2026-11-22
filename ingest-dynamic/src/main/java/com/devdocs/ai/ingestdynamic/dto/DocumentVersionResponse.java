
package com.devdocs.ai.ingestdynamic.dto;

import java.time.OffsetDateTime;

public record DocumentVersionResponse(
        Long id,
        Long documentId,
        String versionLabel,
        String checksum,
        boolean current,
        OffsetDateTime fetchedAt,
        OffsetDateTime createdAt
) {
}
