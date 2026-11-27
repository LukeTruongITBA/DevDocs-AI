package com.devdocs.ai.preretrieval.vectorsearch.dto;

import com.devdocs.ai.preretrieval.filter.MetadataFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * @param filter validated metadata filter parsed from URL parameters; never
 *               bound from the JSON body. Null means the standard flow.
 */
public record VectorSearchRequest(
        Long versionId,
        List<Double> embedding,
        Integer topK,
        @JsonIgnore MetadataFilter filter
) {
    public VectorSearchRequest(Long versionId, List<Double> embedding, Integer topK) {
        this(versionId, embedding, topK, null);
    }

    public VectorSearchRequest withFilter(MetadataFilter metadataFilter) {
        return new VectorSearchRequest(versionId, embedding, topK, metadataFilter);
    }
}
