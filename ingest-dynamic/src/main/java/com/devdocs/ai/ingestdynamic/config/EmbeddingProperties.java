
package com.devdocs.ai.ingestdynamic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        String apiUrl,
        String apiKey,
        String model,
        int dimensions,
        int batchSize
) {
    public EmbeddingProperties {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Embedding API URL must not be blank."
            );
        }

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException(
                    "Embedding model must not be blank."
            );
        }

        if (dimensions != 1536) {
            throw new IllegalArgumentException(
                    "Embedding dimensions must be 1536 to match PostgreSQL VECTOR(1536)."
            );
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Embedding batch size must be greater than zero."
            );
        }
    }
}
