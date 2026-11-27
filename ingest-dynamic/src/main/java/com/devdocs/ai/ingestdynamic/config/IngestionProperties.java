
package com.devdocs.ai.ingestdynamic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
        int chunkSize,
        int chunkOverlap,
        int maxPages,
        int requestTimeoutSeconds,
        int maxBatchSources,
        int maxExtractedCharacters,
        int maxChunksPerSource
) {
    public IngestionProperties(
            int chunkSize,
            int chunkOverlap,
            int maxPages,
            int requestTimeoutSeconds
    ) {
        this(chunkSize, chunkOverlap, maxPages, requestTimeoutSeconds,
                4, 100_000, 200);
    }

    @ConstructorBinding
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

        if (maxBatchSources <= 0 || maxBatchSources > 100) {
            throw new IllegalArgumentException(
                    "Maximum batch sources must be between 1 and 100."
            );
        }

        if (maxExtractedCharacters <= 0) {
            throw new IllegalArgumentException(
                    "Maximum extracted characters must be greater than zero."
            );
        }

        if (maxChunksPerSource <= 0) {
            throw new IllegalArgumentException(
                    "Maximum chunks per source must be greater than zero."
            );
        }
    }
}
