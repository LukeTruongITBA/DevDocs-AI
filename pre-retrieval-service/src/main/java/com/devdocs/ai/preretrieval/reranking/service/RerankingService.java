package com.devdocs.ai.preretrieval.reranking.service;

import com.devdocs.ai.preretrieval.reranking.config.RerankingProperties;
import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Story 4 Task 1: cross-encoder reranking of the wider vector top-N.
 *
 * <p>The cross-encoder scores (query, chunk) pairs jointly. If it is
 * disabled, unreachable, times out, or returns a malformed response, the
 * original vector-similarity order is kept unchanged.
 */
@Service
public class RerankingService {

    private static final Logger log = LoggerFactory.getLogger(RerankingService.class);

    public static final String STRATEGY_CROSS_ENCODER = "cross-encoder";
    public static final String STRATEGY_VECTOR_ORDER = "vector-order";

    private final CrossEncoderClient client;
    private final RerankingProperties properties;

    public RerankingService(CrossEncoderClient client, RerankingProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** Number of vector candidates to retrieve before reranking to {@code topK}. */
    public int candidateCount(int topK) {
        int wider = properties.enabled() ? Math.max(topK, properties.candidateCount()) : topK;
        return Math.min(wider, RerankingProperties.MAX_CANDIDATES);
    }

    public List<RerankedChunk> rerank(String query, List<VectorSearchResult> chunks) {
        return rerankWithDetails(query, chunks).chunks();
    }

    public RerankResult rerankWithDetails(String query, List<VectorSearchResult> chunks) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }
        if (chunks == null) {
            throw new IllegalArgumentException("Chunks must not be null");
        }
        chunks.forEach(chunk -> Objects.requireNonNull(chunk, "Chunk must not be null"));

        if (chunks.isEmpty()) {
            return new RerankResult(List.of(), STRATEGY_VECTOR_ORDER, null, 0, "no candidates");
        }
        if (!properties.enabled()) {
            return vectorOrder(chunks, 0, "reranker disabled");
        }

        long started = System.nanoTime();
        try {
            List<String> texts = chunks.stream()
                    .map(chunk -> chunk.content() == null ? "" : chunk.content())
                    .toList();
            double[] scores = client.score(query, texts);
            long latencyMs = (System.nanoTime() - started) / 1_000_000;

            List<RerankedChunk> reranked = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                reranked.add(new RerankedChunk(chunks.get(i), scores[i], i + 1));
            }
            // Ties keep vector order (stable sort on originalRank).
            reranked.sort(Comparator.comparing(RerankedChunk::rerankScore, Comparator.reverseOrder())
                    .thenComparing(RerankedChunk::originalRank));

            log.info("Cross-encoder rerank: model={}, candidates={}, latencyMs={}",
                    properties.model(), chunks.size(), latencyMs);
            return new RerankResult(List.copyOf(reranked), STRATEGY_CROSS_ENCODER,
                    properties.model(), latencyMs, null);
        } catch (CrossEncoderClient.RerankerUnavailableException exception) {
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            log.warn("Reranker unavailable; keeping vector order: reason={}, candidates={}, latencyMs={}",
                    exception.getMessage(), chunks.size(), latencyMs);
            return vectorOrder(chunks, latencyMs, exception.getMessage());
        }
    }

    private RerankResult vectorOrder(List<VectorSearchResult> chunks, long latencyMs, String reason) {
        List<RerankedChunk> original = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            VectorSearchResult chunk = chunks.get(i);
            original.add(new RerankedChunk(chunk, chunk.similarity(), i + 1));
        }
        return new RerankResult(List.copyOf(original), STRATEGY_VECTOR_ORDER, null, latencyMs, reason);
    }

    /**
     * @param strategy       {@code cross-encoder} or {@code vector-order}
     * @param model          pinned model when the cross-encoder ran, else null
     * @param latencyMs      time spent in the reranker call
     * @param fallbackReason why vector order was kept, else null
     */
    public record RerankResult(
            List<RerankedChunk> chunks,
            String strategy,
            String model,
            long latencyMs,
            String fallbackReason
    ) {
    }
}
