
package com.devdocs.ai.ingestdynamic.controller;

import com.devdocs.ai.ingestdynamic.dto.DynamicIngestionRequest;
import com.devdocs.ai.ingestdynamic.dto.DynamicIngestionBatchRequest;
import com.devdocs.ai.ingestdynamic.service.DynamicIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/ingestion/dynamic")
public class DynamicIngestionController {

    private final DynamicIngestionService ingestionService;

    public DynamicIngestionController(
            DynamicIngestionService ingestionService
    ) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<DynamicIngestionService.IngestionResult> ingest(
            @Valid @RequestBody DynamicIngestionRequest request
    ) throws IOException {

        DynamicIngestionService.IngestionResult result =
                ingestionService.ingest(
                        request.websiteUrl(),
                        request.explicitVersion()
                );

        return ResponseEntity.ok(result);
    }

    @PostMapping("/batch")
    public ResponseEntity<DynamicIngestionService.BatchIngestionResult>
    ingestBatch(@Valid @RequestBody DynamicIngestionBatchRequest request) {
        return ResponseEntity.ok(
                ingestionService.ingestBatch(request.sources())
        );
    }
}
