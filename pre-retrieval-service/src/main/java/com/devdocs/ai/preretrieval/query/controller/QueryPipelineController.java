
package com.devdocs.ai.preretrieval.query.controller;

import com.devdocs.ai.preretrieval.filter.MetadataFilter;
import com.devdocs.ai.preretrieval.filter.MetadataFilterParser;
import com.devdocs.ai.preretrieval.query.dto.QueryAnswerResponse;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineRequest;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineResponse;
import com.devdocs.ai.preretrieval.query.service.QueryAnswerService;
import com.devdocs.ai.preretrieval.query.service.QueryPipelineService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    /**
     * Optional URL metadata filters: repeated {@code filter=field:op:value}
     * and {@code filterMode=AND|OR}. Without them the standard flow runs.
     */
    @PostMapping("/retrieve")
    public ResponseEntity<QueryPipelineResponse> retrieve(
            @RequestBody QueryPipelineRequest request,
            HttpServletRequest httpRequest,
            @RequestParam(value = "filterMode", required = false) String filterMode
    ) {
        MetadataFilter filter = MetadataFilterParser.parse(rawFilters(httpRequest), filterMode);
        return ResponseEntity.ok(
                filter == null
                        ? queryPipelineService.retrieve(request)
                        : queryPipelineService.retrieve(request, filter)
        );
    }

    @PostMapping("/answer")
    public ResponseEntity<QueryAnswerResponse> answer(
            @RequestBody QueryPipelineRequest request,
            HttpServletRequest httpRequest,
            @RequestParam(value = "filterMode", required = false) String filterMode
    ) {
        MetadataFilter filter = MetadataFilterParser.parse(rawFilters(httpRequest), filterMode);
        return ResponseEntity.ok(
                filter == null
                        ? queryAnswerService.answer(request)
                        : queryAnswerService.answer(request, filter)
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

    private static List<String> rawFilters(HttpServletRequest httpRequest) {
        return MetadataFilterParser.rawValues(httpRequest.getParameterValues("filter"));
    }
}
