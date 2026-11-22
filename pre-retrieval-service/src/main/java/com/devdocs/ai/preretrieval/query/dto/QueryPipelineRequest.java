
package com.devdocs.ai.preretrieval.query.dto;

public record QueryPipelineRequest(
        String query,
        Long versionId,
        Integer topK
) {
}
