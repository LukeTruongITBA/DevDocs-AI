package com.devdocs.ai.ingeststatic.controller;

import com.devdocs.ai.ingeststatic.dto.PdfExtractionResponse;
import com.devdocs.ai.ingeststatic.service.PdfExtractionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/ingestion/static")
public class PdfIngestionController {

    private final PdfExtractionService pdfExtractionService;

    public PdfIngestionController(PdfExtractionService pdfExtractionService) {
        this.pdfExtractionService = pdfExtractionService;
    }

    @PostMapping(
            value = "/pdf",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PdfExtractionResponse> uploadPdf(
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        PdfExtractionResponse response = pdfExtractionService.extract(file);

        return ResponseEntity.ok(response);
    }
}
