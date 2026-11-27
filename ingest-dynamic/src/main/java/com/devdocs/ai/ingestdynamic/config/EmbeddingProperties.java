
package com.devdocs.ai.ingestdynamic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        String apiUrl,
        String apiKey,
        String model,
        int dimensions,
        int batchSize,
        int maxAttempts,
        long initialBackoffMillis,
        long maxBackoffMillis,
        double jitterRatio,
        String provider,
        boolean persistenceEnabled,
        long connectTimeoutMillis,
        long readTimeoutMillis
) {
    private static final int MAX_ALLOWED_ATTEMPTS = 5;
    private static final long MAX_ALLOWED_BACKOFF_MILLIS = 60_000;

    /**
     * Keeps programmatic callers on the conservative default policy while
     * the additional retry settings are bound from application.yaml.
     */
    public EmbeddingProperties(
            String apiUrl,
            String apiKey,
            String model,
            int dimensions,
            int batchSize
    ) {
        this(apiUrl, apiKey, model, dimensions, batchSize,
                1, 250, 2_000, 0.2, "gemini", true, 5_000, 120_000);
    }

    public EmbeddingProperties(
            String apiUrl,
            String apiKey,
            String model,
            int dimensions,
            int batchSize,
            int maxAttempts,
            long initialBackoffMillis,
            long maxBackoffMillis,
            double jitterRatio
    ) {
        this(apiUrl, apiKey, model, dimensions, batchSize, maxAttempts,
                initialBackoffMillis, maxBackoffMillis, jitterRatio,
                "gemini", true, 5_000, 120_000);
    }

    public EmbeddingProperties(
            String apiUrl,
            String apiKey,
            String model,
            int dimensions,
            int batchSize,
            String provider
    ) {
        this(apiUrl, apiKey, model, dimensions, batchSize,
                1, 250, 2_000, 0.2, provider, false, 5_000, 120_000);
    }

    @ConstructorBinding
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

        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Embedding provider must not be blank.");
        }

        if (!provider.equalsIgnoreCase("ollama") && dimensions != 1536) {
            throw new IllegalArgumentException(
                    "Embedding dimensions must be 1536 to match PostgreSQL VECTOR(1536)."
            );
        }

        if (provider.equalsIgnoreCase("ollama") && dimensions != 768) {
            throw new IllegalArgumentException("Ollama embedding dimensions must be 768.");
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Embedding batch size must be greater than zero."
            );
        }

        if (maxAttempts <= 0 || maxAttempts > MAX_ALLOWED_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "Embedding maximum attempts must be between 1 and "
                            + MAX_ALLOWED_ATTEMPTS + "."
            );
        }

        if (initialBackoffMillis < 0) {
            throw new IllegalArgumentException(
                    "Embedding initial backoff must not be negative."
            );
        }

        if (maxBackoffMillis < initialBackoffMillis
                || maxBackoffMillis > MAX_ALLOWED_BACKOFF_MILLIS) {
            throw new IllegalArgumentException(
                    "Embedding maximum backoff must not be smaller than "
                            + "the initial backoff or exceed "
                            + MAX_ALLOWED_BACKOFF_MILLIS + " ms."
            );
        }

        if (jitterRatio < 0 || jitterRatio > 1) {
            throw new IllegalArgumentException(
                    "Embedding jitter ratio must be between 0 and 1."
            );
        }

        if (connectTimeoutMillis < 0 || readTimeoutMillis < 0) {
            throw new IllegalArgumentException("Embedding timeouts must not be negative.");
        }
    }

    public boolean isOllama() {
        return "ollama".equalsIgnoreCase(provider);
    }

    public boolean persistenceReady() {
        return !isOllama() || persistenceEnabled;
    }
}
