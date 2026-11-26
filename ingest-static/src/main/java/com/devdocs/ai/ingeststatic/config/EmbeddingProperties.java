package com.devdocs.ai.ingeststatic.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.embedding")
@Validated
public record EmbeddingProperties(

        @NotBlank
        String apiUrl,

        String apiKey,

        @NotBlank
        String model,

        @Min(1)
        @Max(3072)
        int dimensions,

        @Min(1)
        @Max(2048)
        int batchSize

) {
    public EmbeddingProperties {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException("Embedding API URL must not be blank.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Embedding model must not be blank.");
        }
        if (dimensions != 1536) {
            throw new IllegalArgumentException(
                    "Embedding dimensions must be 1536 to match PostgreSQL VECTOR(1536)."
            );
        }
        if (batchSize <= 0 || batchSize > 2048) {
            throw new IllegalArgumentException("Embedding batch size must be between 1 and 2048.");
        }
    }
}
