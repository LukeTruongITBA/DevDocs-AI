
package com.devdocs.ai.preretrieval.reranking.service;

import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RerankingService {

    private static final double SIMILARITY_WEIGHT = 0.7;
    private static final double KEYWORD_WEIGHT = 0.3;

    private static final Pattern WORD_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}]+");

    public List<RerankedChunk> rerank(
            String query,
            List<VectorSearchResult> chunks
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }

        if (chunks == null) {
            throw new IllegalArgumentException("Chunks must not be null");
        }

        Set<String> queryTerms = extractTerms(query);

        List<RerankedChunk> results = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            VectorSearchResult chunk = Objects.requireNonNull(
                    chunks.get(i),
                    "Chunk must not be null"
            );

            double similarity = chunk.similarity() == null
                    ? 0.0
                    : chunk.similarity();

            if (!Double.isFinite(similarity)) {
                throw new IllegalArgumentException(
                        "Chunk similarity must be finite"
                );
            }

            double normalizedSimilarity =
                    Math.max(0.0, Math.min(1.0, similarity));

            double keywordOverlap = calculateKeywordOverlap(
                    queryTerms,
                    chunk.content()
            );

            double score =
                    SIMILARITY_WEIGHT * normalizedSimilarity
                            + KEYWORD_WEIGHT * keywordOverlap;

            results.add(new RerankedChunk(
                    chunk,
                    score,
                    i + 1
            ));
        }

        results.sort(
                Comparator.comparing(
                                RerankedChunk::rerankScore,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                result -> result.chunk().similarity(),
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                        .thenComparing(
                                result -> result.chunk().chunkIndex(),
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
        );

        return List.copyOf(results);
    }

    private double calculateKeywordOverlap(
            Set<String> queryTerms,
            String content
    ) {
        if (queryTerms.isEmpty() || content == null || content.isBlank()) {
            return 0.0;
        }

        Set<String> contentTerms = extractTerms(content);

        long matchedTerms = queryTerms.stream()
                .filter(contentTerms::contains)
                .count();

        return (double) matchedTerms / queryTerms.size();
    }

    private Set<String> extractTerms(String text) {
        return WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT))
                .results()
                .map(match -> match.group())
                .collect(Collectors.toUnmodifiableSet());
    }
}
