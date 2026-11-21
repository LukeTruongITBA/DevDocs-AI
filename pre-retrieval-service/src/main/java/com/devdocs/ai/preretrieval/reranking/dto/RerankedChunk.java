
package com.devdocs.ai.preretrieval.reranking.dto;

import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;

public record RerankedChunk(
        VectorSearchResult chunk,
        Double rerankScore,
        Integer originalRank
) {
}
