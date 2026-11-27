package com.devdocs.ai.ingestdynamic.embedding;

import java.util.Map;

/**
 * A bounded, provider-safe description of a failed Gemini embedding request.
 * The exception deliberately contains no raw response body or credentials.
 */
public final class GeminiEmbeddingException extends RuntimeException {

    private final int statusCode;
    private final String providerCode;
    private final String providerMessage;
    private final String retryAfter;
    private final Long retryAfterMillis;
    private final Map<String, String> rateLimitHeaders;
    private final String requestId;

    public GeminiEmbeddingException(
            int statusCode,
            String providerCode,
            String providerMessage,
            String retryAfter,
            Long retryAfterMillis,
            Map<String, String> rateLimitHeaders,
            String requestId
    ) {
        super(createMessage(
                statusCode,
                providerCode,
                providerMessage,
                retryAfter,
                requestId
        ));
        this.statusCode = statusCode;
        this.providerCode = providerCode;
        this.providerMessage = providerMessage;
        this.retryAfter = retryAfter;
        this.retryAfterMillis = retryAfterMillis;
        this.rateLimitHeaders = Map.copyOf(rateLimitHeaders);
        this.requestId = requestId;
    }

    public int statusCode() {
        return statusCode;
    }

    public String providerCode() {
        return providerCode;
    }

    public String providerMessage() {
        return providerMessage;
    }

    public String retryAfter() {
        return retryAfter;
    }

    public Long retryAfterMillis() {
        return retryAfterMillis;
    }

    public Map<String, String> rateLimitHeaders() {
        return rateLimitHeaders;
    }

    public String requestId() {
        return requestId;
    }

    public boolean retryable() {
        return statusCode == 408
                || statusCode == 425
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private static String createMessage(
            int statusCode,
            String providerCode,
            String providerMessage,
            String retryAfter,
            String requestId
    ) {
        StringBuilder message = new StringBuilder(
                "Gemini embedding request failed: HTTP "
                        + statusCode
        );
        append(message, "code", providerCode);
        append(message, "message", providerMessage);
        append(message, "retry-after", retryAfter);
        append(message, "request-id", requestId);
        return message.toString();
    }

    private static void append(
            StringBuilder message,
            String name,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            message.append("; ").append(name).append('=').append(value);
        }
    }
}
