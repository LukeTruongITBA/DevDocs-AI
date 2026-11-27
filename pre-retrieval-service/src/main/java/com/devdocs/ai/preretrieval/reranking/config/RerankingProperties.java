package com.devdocs.ai.preretrieval.reranking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Cross-encoder reranker (Hugging Face Text Embeddings Inference /rerank).
 *
 * @param enabled             false = always keep vector order
 * @param url                 TEI base URL
 * @param model               pinned model id@revision, reported in responses
 * @param candidateCount      wider top-N retrieved before reranking to top-k
 * @param connectTimeoutMillis connect timeout
 * @param readTimeoutMillis   read timeout; on expiry the vector order is kept
 */
@ConfigurationProperties(prefix = "app.reranking")
public record RerankingProperties(
        boolean enabled,
        String url,
        String model,
        int candidateCount,
        long connectTimeoutMillis,
        long readTimeoutMillis
) {
    public static final int MAX_CANDIDATES = 100;

    @ConstructorBinding
    public RerankingProperties {
        if (enabled && (url == null || url.isBlank())) {
            throw new IllegalArgumentException("Reranker URL must not be blank when enabled.");
        }
        if (candidateCount < 1 || candidateCount > MAX_CANDIDATES) {
            throw new IllegalArgumentException(
                    "Reranker candidate count must be between 1 and " + MAX_CANDIDATES + ".");
        }
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Reranker timeouts must be positive.");
        }
    }
}
