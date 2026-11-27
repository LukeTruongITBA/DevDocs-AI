
package com.devdocs.ai.document;

import com.devdocs.ai.document.dto.CreateDocumentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<Document> create(
            @Valid @RequestBody CreateDocumentRequest request
    ) {
        return create(request, false);
    }

    @PostMapping(params = "rejectExisting")
    public ResponseEntity<Document> createRejectingExisting(
            @Valid @RequestBody CreateDocumentRequest request,
            @RequestParam boolean rejectExisting
    ) {
        return create(request, rejectExisting);
    }

    private ResponseEntity<Document> create(
            CreateDocumentRequest request,
            boolean rejectExisting
    ) {
        Document document = rejectExisting
                ? documentService.createRejectingExisting(request)
                : documentService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(document);
    }

    @GetMapping
    public ResponseEntity<List<Document>> findAll() {
        return ResponseEntity.ok(documentService.findAll());
    }

    /**
     * Indexed lookup of a WEB document by its exact source URI. Returns a
     * list with zero or one element, so "not found" is never confused with
     * a missing route.
     */
    @GetMapping("/web")
    public ResponseEntity<List<Document>> findWebBySourceUri(
            @RequestParam String sourceUri
    ) {
        return ResponseEntity.ok(documentService.findWebBySourceUri(sourceUri));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> findById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(documentService.findById(id));
    }
}
