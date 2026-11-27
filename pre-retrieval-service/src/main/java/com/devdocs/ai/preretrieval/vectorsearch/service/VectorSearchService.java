package com.devdocs.ai.preretrieval.vectorsearch.service;

import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchRequest;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import com.devdocs.ai.preretrieval.vectorsearch.repository.VectorSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorSearchService {

    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final int MAX_TOP_K = 100;

    private final VectorSearchRepository vectorSearchRepository;

    public VectorSearchService(
            VectorSearchRepository vectorSearchRepository
    ) {
        this.vectorSearchRepository = vectorSearchRepository;
    }

    public List<VectorSearchResult> search(VectorSearchRequest request) {
        validateRequest(request);

        return vectorSearchRepository.search(
                request.versionId(),
                request.embedding(),
                request.topK()
        );
    }

    private void validateRequest(VectorSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Search request must not be null"
            );
        }

        if (request.versionId() == null || request.versionId() <= 0) {
            throw new IllegalArgumentException(
                    "versionId must be greater than zero"
            );
        }

        List<Double> embedding = request.embedding();

        if (embedding != null && embedding.size() == 768) {
            throw new IllegalArgumentException(
                    "Ollama retrieval is disabled until chunks.embedding_ollama VECTOR(768) is migrated."
            );
        }

        if (embedding == null || embedding.size() != EMBEDDING_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Embedding must contain exactly 1536 dimensions"
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
