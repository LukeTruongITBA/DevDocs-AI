package com.devdocs.ai.ingeststatic.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
        int batchSize,

        String provider,
        long connectTimeoutMillis,
        long readTimeoutMillis

) {
    @ConstructorBinding
    public EmbeddingProperties {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException("Embedding API URL must not be blank.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Embedding model must not be blank.");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Embedding provider must not be blank.");
        }
        if (provider.equalsIgnoreCase("ollama") && dimensions != 768) {
            throw new IllegalArgumentException("Ollama embedding dimensions must be 768.");
        }
        if (!provider.equalsIgnoreCase("ollama") && dimensions != 1536) {
            throw new IllegalArgumentException(
                    "Embedding dimensions must be 1536 to match PostgreSQL VECTOR(1536)."
            );
        }
        if (batchSize <= 0 || batchSize > 2048) {
            throw new IllegalArgumentException("Embedding batch size must be between 1 and 2048.");
        }
        if (connectTimeoutMillis < 0 || readTimeoutMillis < 0) {
            throw new IllegalArgumentException("Embedding timeouts must not be negative.");
        }
    }

    public EmbeddingProperties(
            String apiUrl,
            String apiKey,
            String model,
            int dimensions,
            int batchSize
    ) {
        this(apiUrl, apiKey, model, dimensions, batchSize, "gemini", 5_000, 120_000);
    }

    public EmbeddingProperties(
            String apiUrl, String apiKey, String model, int dimensions,
            int batchSize, String provider
    ) {
        this(apiUrl, apiKey, model, dimensions, batchSize, provider, 5_000, 120_000);
    }

    public boolean isOllama() {
        return "ollama".equalsIgnoreCase(provider);
    }
}
