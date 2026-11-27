package com.devdocs.ai.preretrieval.reranking.service;

import com.devdocs.ai.preretrieval.reranking.config.RerankingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Calls TEI {@code POST /rerank}. Returns one relevance score per input text,
 * in input order. Any transport, HTTP or shape problem raises
 * {@link RerankerUnavailableException}; the caller then keeps vector order.
 */
@Component
public class CrossEncoderClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public CrossEncoderClient(RestClient.Builder builder, RerankingProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, properties.connectTimeoutMillis()));
        factory.setReadTimeout((int) Math.min(Integer.MAX_VALUE, properties.readTimeoutMillis()));
        this.restClient = builder.clone()
                .baseUrl(properties.url() == null ? "http://localhost" : properties.url())
                .requestFactory(factory)
                .build();
    }

    CrossEncoderClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public double[] score(String query, List<String> texts) {
        byte[] body;
        try {
            body = restClient.post()
                    .uri("/rerank")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "query", query,
                            "texts", texts,
                            "truncate", true,
                            "raw_scores", false))
                    .retrieve()
                    .body(byte[].class);
        } catch (RuntimeException exception) {
            throw new RerankerUnavailableException(
                    "Reranker request failed: " + exception.getClass().getSimpleName(), exception);
        }

        JsonNode array;
        try {
            array = body == null ? null : objectMapper.readTree(body);
        } catch (RuntimeException exception) {
            throw new RerankerUnavailableException("Reranker returned invalid JSON", exception);
        }
        if (array == null || !array.isArray() || array.size() != texts.size()) {
            throw new RerankerUnavailableException("Reranker returned an unexpected number of scores");
        }

        double[] scores = new double[texts.size()];
        Arrays.fill(scores, Double.NaN);
        for (JsonNode item : array) {
            JsonNode index = item.get("index");
            JsonNode score = item.get("score");
            if (index == null || !index.canConvertToInt() || score == null || !score.isNumber()) {
                throw new RerankerUnavailableException("Reranker returned a malformed score entry");
            }
            int i = index.intValue();
            double value = score.doubleValue();
            if (i < 0 || i >= scores.length || !Double.isNaN(scores[i]) || !Double.isFinite(value)) {
                throw new RerankerUnavailableException("Reranker returned an invalid score entry");
            }
            scores[i] = value;
        }
        return scores;
    }

    public static class RerankerUnavailableException extends RuntimeException {
        public RerankerUnavailableException(String message) {
            super(message);
        }

        public RerankerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
