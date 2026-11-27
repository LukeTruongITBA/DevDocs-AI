package com.devdocs.ai.preretrieval.vectorsearch.service;

import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchRequest;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import com.devdocs.ai.preretrieval.vectorsearch.repository.EmbeddingColumn;
import com.devdocs.ai.preretrieval.vectorsearch.repository.VectorSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);
    private static final int MAX_TOP_K = 100;

    private final VectorSearchRepository vectorSearchRepository;

    public VectorSearchService(
            VectorSearchRepository vectorSearchRepository
    ) {
        this.vectorSearchRepository = vectorSearchRepository;
    }

    public List<VectorSearchResult> search(VectorSearchRequest request) {
        validateRequest(request);

        if (request.filter() == null) {
            // Standard flow: unchanged version-scoped search.
            return vectorSearchRepository.search(
                    request.versionId(),
                    request.embedding(),
                    request.topK()
            );
        }

        List<VectorSearchResult> results = vectorSearchRepository.search(
                request.versionId(),
                request.embedding(),
                request.topK(),
                request.filter()
        );
        log.info("Filtered vector search: mode={}, conditions={}, versionId={}, topK={}, results={}",
                request.filter().mode(), request.filter().describe(),
                request.versionId(), request.topK(), results.size());
        return results;
    }

    private void validateRequest(VectorSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Search request must not be null"
            );
        }

        // versionId scopes the standard flow. With a metadata filter it is
        // optional (the filter scopes the search); if given it must be valid.
        if (request.filter() == null
                ? request.versionId() == null || request.versionId() <= 0
                : request.versionId() != null && request.versionId() <= 0) {
            throw new IllegalArgumentException(
                    "versionId must be greater than zero"
            );
        }

        List<Double> embedding = request.embedding();

        if (embedding == null
                || EmbeddingColumn.forDimensions(embedding.size()).isEmpty()) {
            throw new IllegalArgumentException(
                    "Embedding must contain exactly 768 (Ollama) or 1536 (Gemini) dimensions"
            );
        }

        for (int i = 0; i < embedding.size(); i++) {
            Double value = embedding.get(i);

            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Embedding contains an invalid value at index " + i
                );
            }
        }

        if (request.topK() == null
                || request.topK() < 1
                || request.topK() > MAX_TOP_K) {
            throw new IllegalArgumentException(
                    "topK must be between 1 and 100"
            );
        }
    }
}
