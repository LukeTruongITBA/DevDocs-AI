
package com.devdocs.ai.preretrieval.query.controller;

import com.devdocs.ai.preretrieval.query.dto.QueryAnswerResponse;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineRequest;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineResponse;
import com.devdocs.ai.preretrieval.query.service.QueryAnswerService;
import com.devdocs.ai.preretrieval.query.service.QueryPipelineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/query")
public class QueryPipelineController {

    private final QueryPipelineService queryPipelineService;
    private final QueryAnswerService queryAnswerService;

    public QueryPipelineController(
            QueryPipelineService queryPipelineService,
            QueryAnswerService queryAnswerService
    ) {
        this.queryPipelineService = queryPipelineService;
        this.queryAnswerService = queryAnswerService;
    }

    @PostMapping("/retrieve")
    public ResponseEntity<QueryPipelineResponse> retrieve(
            @RequestBody QueryPipelineRequest request
    ) {
        return ResponseEntity.ok(
                queryPipelineService.retrieve(request)
        );
    }

    @PostMapping("/answer")
    public ResponseEntity<QueryAnswerResponse> answer(
            @RequestBody QueryPipelineRequest request
    ) {
        return ResponseEntity.ok(
                queryAnswerService.answer(request)
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
