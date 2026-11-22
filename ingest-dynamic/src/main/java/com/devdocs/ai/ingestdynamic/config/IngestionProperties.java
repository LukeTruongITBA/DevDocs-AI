
package com.devdocs.ai.ingestdynamic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
        int chunkSize,
        int chunkOverlap,
        int maxPages,
        int requestTimeoutSeconds
) {
    public IngestionProperties {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "Chunk size must be greater than zero."
            );
        }

        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "Chunk overlap must be non-negative and smaller than chunk size."
            );
        }

        if (maxPages <= 0) {
            throw new IllegalArgumentException(
                    "Maximum pages must be greater than zero."
            );
        }

        if (requestTimeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Request timeout must be greater than zero."
            );
        }
    }
}
