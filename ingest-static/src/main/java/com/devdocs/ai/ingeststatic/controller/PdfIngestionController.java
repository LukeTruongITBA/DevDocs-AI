
package com.devdocs.ai.ingeststatic.controller;

import com.devdocs.ai.ingeststatic.dto.ChunkPersistenceResult;
import com.devdocs.ai.ingeststatic.dto.PdfExtractionResponse;
import com.devdocs.ai.ingeststatic.service.ChunkPersistenceService;
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
    private final ChunkPersistenceService chunkPersistenceService;

    public PdfIngestionController(
            PdfExtractionService pdfExtractionService,
            ChunkPersistenceService chunkPersistenceService
    ) {
        this.pdfExtractionService = pdfExtractionService;
        this.chunkPersistenceService = chunkPersistenceService;
    }

    @PostMapping(
            value = "/pdf",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PdfExtractionResponse> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentId") Long documentId,
            @RequestParam("versionId") Long versionId
    ) throws IOException {

        PdfExtractionResponse extractionResponse =
                pdfExtractionService.extract(file);

        ChunkPersistenceResult persistenceResult =
                chunkPersistenceService.persist(
                        documentId,
                        versionId,
                        file,
                        extractionResponse.chunks()
                );

        PdfExtractionResponse response = new PdfExtractionResponse(
                extractionResponse.fileName(),
                extractionResponse.pageCount(),
                extractionResponse.characterCount(),
                extractionResponse.extractedText(),
                extractionResponse.pages(),
                extractionResponse.chunks(),
                persistenceResult
        );

        return ResponseEntity.ok(response);
    }
}
