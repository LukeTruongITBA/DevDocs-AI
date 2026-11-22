
package com.devdocs.ai.preretrieval.embedding.controller;

import com.devdocs.ai.preretrieval.embedding.dto.QueryEmbeddingResponse;
import com.devdocs.ai.preretrieval.embedding.service.QueryEmbeddingService;
import com.devdocs.ai.preretrieval.query.dto.QueryNormalizationRequest;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query")
public class QueryEmbeddingController {

    private final QueryEmbeddingService queryEmbeddingService;

    public QueryEmbeddingController(
            QueryEmbeddingService queryEmbeddingService
    ) {
        this.queryEmbeddingService = queryEmbeddingService;
    }

    @PostMapping("/embed")
    public QueryEmbeddingResponse embed(
            @RequestBody QueryNormalizationRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }

        var embedding = queryEmbeddingService.embed(request.query());

        return new QueryEmbeddingResponse(
                true,
                embedding.size()
        );
    }
}
