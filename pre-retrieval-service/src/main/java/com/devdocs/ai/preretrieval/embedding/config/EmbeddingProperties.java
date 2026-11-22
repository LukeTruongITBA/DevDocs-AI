
package com.devdocs.ai.preretrieval.embedding.config;

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
                        throw new IllegalArgumentException("Embedding API URL must not be blank");
                }

                if (model == null || model.isBlank()) {
                        throw new IllegalArgumentException("Embedding model must not be blank");
                }

                if (dimensions <= 0 || dimensions > 3072) {
                        throw new IllegalArgumentException("Embedding dimensions must be between 1 and 3072");
                }

                if (batchSize <= 0 || batchSize > 2048) {
                        throw new IllegalArgumentException("Embedding batch size must be between 1 and 2048");
                }
        }
}
