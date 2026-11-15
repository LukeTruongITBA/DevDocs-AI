
package com.devdocs.ai.document;

import java.time.OffsetDateTime;

public record Document(
        Long id,
        String title,
        String sourceType,
        String sourceUri,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
