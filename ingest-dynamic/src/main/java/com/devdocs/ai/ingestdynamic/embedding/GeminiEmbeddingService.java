package com.devdocs.ai.ingestdynamic.embedding;

import com.devdocs.ai.ingestdynamic.config.EmbeddingProperties;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiEmbeddingService implements EmbeddingService {

    private static final Logger log =
            LoggerFactory.getLogger(GeminiEmbeddingService.class);
    private static final int MAX_ERROR_BODY_BYTES = 8 * 1024;
    private static final int MAX_SUCCESS_BODY_BYTES = 8 * 1024 * 1024;
    private static final long MAX_RETRY_AFTER_MILLIS = 60_000;
    private static final Pattern CONTROL_CHARACTERS =
            Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(api[-_ ]?key|authorization|bearer|token|password|secret)"
                    + "\\s*[:=]\\s*(\\\"[^\\\"]*\\\"|[^,;\\s]+)"
    );
    private static final List<String> RATE_LIMIT_HEADER_NAMES = List.of(
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "X-Goog-Quota-Limit",
            "X-Goog-Quota-Remaining",
            "X-Goog-Quota-Reset"
    );
    private static final List<String> REQUEST_ID_HEADER_NAMES = List.of(
            "X-Goog-Request-Id",
            "X-Request-Id",
            "Request-Id",
            "Trace-Id"
    );

    private final EmbeddingProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RetrySleeper retrySleeper;
    private final DoubleSupplier random;

    public GeminiEmbeddingService(
            EmbeddingProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this(properties, restClientBuilder, new ObjectMapper());
    }

    @Autowired
    public GeminiEmbeddingService(
            EmbeddingProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this(properties, restClientBuilder, objectMapper,
                Thread::sleep,
                () -> ThreadLocalRandom.current().nextDouble()
        );
    }

    GeminiEmbeddingService(
            EmbeddingProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            RetrySleeper retrySleeper,
            DoubleSupplier random
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.retrySleeper = retrySleeper;
        this.random = random;
    }

    public List<float[]> embed(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chunks must not be null or empty."
            );
        }

        if (properties.apiKey() == null
                || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is not configured."
            );
        }

        List<float[]> embeddings = new ArrayList<>();

        for (int start = 0;
             start < chunks.size();
             start += properties.batchSize()) {

            int end = Math.min(
                    start + properties.batchSize(),
                    chunks.size()
            );

            List<TextChunk> batch = chunks.subList(start, end);

            embeddings.addAll(embedBatchWithRetry(batch));
        }

        return List.copyOf(embeddings);
    }

    private List<float[]> embedBatchWithRetry(List<TextChunk> batch) {
        Map<String, Object> requestBody = createRequestBody(batch);

        for (int attempt = 1;
             attempt <= properties.maxAttempts();
             attempt++) {
            try {
                return parseEmbeddingResponse(
                        executeRequest(requestBody),
                        batch
                );
            } catch (GeminiEmbeddingException exception) {
                log.warn(
                        "Gemini embedding upstream failure: status={}, "
                                + "providerCode={}, providerMessage={}, "
                                + "retryAfter={}, rateLimitHeaders={}, "
                                + "requestId={}, attempt={}/{}",
                        exception.statusCode(),
                        exception.providerCode(),
                        exception.providerMessage(),
                        exception.retryAfter(),
                        exception.rateLimitHeaders(),
                        exception.requestId(),
                        attempt,
                        properties.maxAttempts()
                );

                if (!exception.retryable()
                        || attempt >= properties.maxAttempts()) {
                    throw exception;
                }

                long delayMillis = retryDelayMillis(exception, attempt);
                log.warn(
                        "Retrying Gemini embedding batch after {} ms "
                                + "(status={}, attempt={}/{})",
                        delayMillis,
                        exception.statusCode(),
                        attempt + 1,
                        properties.maxAttempts()
                );
                sleepBeforeRetry(delayMillis);
            }
        }

        throw new IllegalStateException(
                "Gemini embedding retry policy ended unexpectedly."
        );
    }

    private Map<String, Object> createRequestBody(List<TextChunk> batch) {
        List<Map<String, Object>> requests = new ArrayList<>();

        for (TextChunk chunk : batch) {
            Map<String, Object> content = Map.of(
                    "parts",
                    List.of(Map.of("text", chunk.content()))
            );

            Map<String, Object> request = new HashMap<>();
            request.put("model", properties.model());
            request.put("content", content);
            request.put(
                    "outputDimensionality",
                    properties.dimensions()
            );

            requests.add(request);
        }

        return Map.of("requests", requests);
    }

    private JsonNode executeRequest(Map<String, Object> requestBody) {
        return restClient.post()
                .uri(properties.apiUrl())
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    BodyReadResult body = readBody(
                            response.getBody(),
                            status >= 400
                                    ? MAX_ERROR_BODY_BYTES
                                    : MAX_SUCCESS_BODY_BYTES
                    );

                    if (status < 200 || status >= 300) {
                        throw createUpstreamException(
                                status,
                                response.getHeaders(),
                                body.text()
                        );
                    }

                    if (body.truncated()) {
                        throw new IllegalStateException(
                                "Gemini returned an embedding response "
                                        + "larger than the configured safety limit."
                        );
                    }

                    try {
                        return objectMapper.readTree(body.text());
                    } catch (JacksonException exception) {
                        throw new IllegalStateException(
                                "Gemini returned an invalid JSON response.",
                                exception
                        );
                    }
                });
    }

    private List<float[]> parseEmbeddingResponse(
            JsonNode response,
            List<TextChunk> batch
    ) {
        if (response == null
                || !response.has("embeddings")
                || !response.get("embeddings").isArray()) {
            throw new IllegalStateException(
                    "Gemini returned an invalid embedding response."
            );
        }

        JsonNode embeddingNodes = response.get("embeddings");

        if (embeddingNodes.size() != batch.size()) {
            throw new IllegalStateException(
                    "Gemini returned an unexpected number of embeddings."
            );
        }

        List<float[]> result = new ArrayList<>();

        for (JsonNode embeddingNode : embeddingNodes) {
            JsonNode values = embeddingNode.get("values");

            if (values == null || !values.isArray()
                    || values.size() != properties.dimensions()) {
                throw new IllegalStateException(
                        "Gemini returned an embedding with invalid dimensions."
                );
            }

            float[] vector = new float[values.size()];

            for (int i = 0; i < values.size(); i++) {
                float value = values.get(i).floatValue();

                if (!Float.isFinite(value)) {
                    throw new IllegalStateException(
                            "Gemini returned a non-finite embedding value."
                    );
                }

                vector[i] = value;
            }

            result.add(vector);
        }

        return result;
    }

    private GeminiEmbeddingException createUpstreamException(
            int status,
            HttpHeaders headers,
            String responseBody
    ) {
        ProviderError providerError = parseProviderError(responseBody);
        String retryAfter = sanitize(headers.getFirst("Retry-After"), 100);
        Long retryAfterMillis = parseRetryAfterMillis(retryAfter);
        Map<String, String> rateLimitHeaders = rateLimitHeaders(headers);
        String requestId = firstHeader(headers, REQUEST_ID_HEADER_NAMES);

        return new GeminiEmbeddingException(
                status,
                providerError.code(),
                providerError.message(),
                retryAfter,
                retryAfterMillis,
                rateLimitHeaders,
                requestId
        );
    }

    private ProviderError parseProviderError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new ProviderError(
                    null,
                    "Provider returned no diagnostic message."
            );
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root == null ? null : root.get("error");
            String code = firstJsonText(error, "status", "code");
            if (code == null) {
                code = firstJsonText(root, "status", "code");
            }

            String message = firstJsonText(error, "message");
            if (message == null) {
                message = firstJsonText(root, "message");
            }

            return new ProviderError(
                    sanitize(code, 120),
                    sanitize(
                            message == null
                                    ? "Provider returned an error."
                                    : message,
                            300
                    )
            );
        } catch (RuntimeException exception) {
            return new ProviderError(
                    null,
                    "Provider error response could not be parsed."
            );
        }
    }

    private String firstJsonText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }

        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isValueNode()) {
                String text = value.asString();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private Map<String, String> rateLimitHeaders(HttpHeaders headers) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : RATE_LIMIT_HEADER_NAMES) {
            String value = firstHeader(headers, List.of(name));
            if (value != null && !value.isBlank()) {
                values.put(name, value);
            }
        }
        return values;
    }

    private String firstHeader(
            HttpHeaders headers,
            List<String> names
    ) {
        for (String name : names) {
            String value = sanitize(headers.getFirst(name), 200);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String sanitize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String sanitized = CONTROL_CHARACTERS.matcher(value)
                .replaceAll(" ")
                .trim();
        sanitized = SECRET_VALUE.matcher(sanitized)
                .replaceAll("$1=[REDACTED]");

        return sanitized.length() <= maxLength
                ? sanitized
                : sanitized.substring(0, maxLength);
    }

    private Long parseRetryAfterMillis(String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }

        try {
            long seconds = Long.parseLong(retryAfter.trim());
            if (seconds < 0) {
                return null;
            }
            return Math.min(
                    Math.multiplyExact(seconds, 1_000L),
                    MAX_RETRY_AFTER_MILLIS
            );
        } catch (NumberFormatException | ArithmeticException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(
                        retryAfter,
                        DateTimeFormatter.RFC_1123_DATE_TIME
                ).toInstant();
                long delay = Math.max(
                        0,
                        retryAt.toEpochMilli() - Instant.now().toEpochMilli()
                );
                return Math.min(delay, MAX_RETRY_AFTER_MILLIS);
            } catch (DateTimeParseException ignoredDate) {
                return null;
            }
        }
    }

    private long retryDelayMillis(
            GeminiEmbeddingException exception,
            int attempt
    ) {
        if (exception.retryAfterMillis() != null) {
            return Math.min(
                    exception.retryAfterMillis(),
                    properties.maxBackoffMillis()
            );
        }

        long exponential = properties.initialBackoffMillis();
        for (int index = 1; index < attempt; index++) {
            if (exponential >= properties.maxBackoffMillis()) {
                exponential = properties.maxBackoffMillis();
                break;
            }
            exponential = Math.min(
                    properties.maxBackoffMillis(),
                    exponential * 2
            );
        }

        if (exponential == 0 || properties.jitterRatio() == 0) {
            return exponential;
        }

        long jitter = (long) Math.ceil(
                exponential
                        * properties.jitterRatio()
                        * random.getAsDouble()
        );
        return Math.min(
                properties.maxBackoffMillis(),
                exponential + jitter
        );
    }

    private void sleepBeforeRetry(long delayMillis) {
        try {
            retrySleeper.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Gemini embedding retry was interrupted.",
                    exception
            );
        }
    }

    private BodyReadResult readBody(InputStream input, int maxBytes) {
        if (input == null) {
            return new BodyReadResult("", false);
        }

        try {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            boolean truncated = bytes.length > maxBytes;
            if (truncated) {
                bytes = java.util.Arrays.copyOf(bytes, maxBytes);
            }
            return new BodyReadResult(
                    new String(bytes, StandardCharsets.UTF_8),
                    truncated
            );
        } catch (IOException exception) {
            return new BodyReadResult("", false);
        }
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(long delayMillis) throws InterruptedException;
    }

    private record ProviderError(String code, String message) {
    }

    private record BodyReadResult(String text, boolean truncated) {
    }
}
