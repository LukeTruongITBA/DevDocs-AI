
package com.devdocs.ai.preretrieval.query.dto;

import java.util.List;

public record QueryNormalizationResponse(
        String originalQuery,
        String normalizedQuery,
        List<String> synonymTerms
) {
}
