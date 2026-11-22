
package com.devdocs.ai.ingestdynamic.dto;

import java.time.OffsetDateTime;

public record DocumentResponse(
        Long id,
        String title,
        String sourceType,
        String sourceUri,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
