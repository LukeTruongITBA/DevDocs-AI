package com.devdocs.ai.preretrieval.vectorsearch.controller;

import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchRequest;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import com.devdocs.ai.preretrieval.vectorsearch.service.VectorSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/retrieval")
public class VectorSearchController {

    private final VectorSearchService vectorSearchService;

    public VectorSearchController(
            VectorSearchService vectorSearchService
    ) {
        this.vectorSearchService = vectorSearchService;
    }

    @PostMapping("/search")
    public ResponseEntity<List<VectorSearchResult>> search(
            @RequestBody VectorSearchRequest request
    ) {
        return ResponseEntity.ok(
                vectorSearchService.search(request)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", exception.getMessage()));
    }
}