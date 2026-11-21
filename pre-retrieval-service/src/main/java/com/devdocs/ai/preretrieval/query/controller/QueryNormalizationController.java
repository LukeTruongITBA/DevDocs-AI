
package com.devdocs.ai.preretrieval.query.controller;

import com.devdocs.ai.preretrieval.query.dto.QueryNormalizationRequest;
import com.devdocs.ai.preretrieval.query.dto.QueryNormalizationResponse;
import com.devdocs.ai.preretrieval.query.service.QueryNormalizationService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query")
public class QueryNormalizationController {

    private final QueryNormalizationService queryNormalizationService;

    public QueryNormalizationController(
            QueryNormalizationService queryNormalizationService
    ) {
        this.queryNormalizationService = queryNormalizationService;
    }

    @PostMapping("/normalize")
    public QueryNormalizationResponse normalize(
            @RequestBody QueryNormalizationRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }

        return queryNormalizationService.normalize(request.query());
    }
}
