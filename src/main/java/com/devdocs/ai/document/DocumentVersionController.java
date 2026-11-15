
package com.devdocs.ai.document;

import com.devdocs.ai.document.dto.CreateDocumentVersionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/versions")
public class DocumentVersionController {

    private final DocumentVersionService versionService;

    public DocumentVersionController(
            DocumentVersionService versionService
    ) {
        this.versionService = versionService;
    }

    @PostMapping
    public ResponseEntity<DocumentVersion> create(
            @PathVariable Long documentId,
            @Valid @RequestBody CreateDocumentVersionRequest request
    ) {
        DocumentVersion version =
                versionService.create(documentId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(version);
    }

    @GetMapping
    public ResponseEntity<List<DocumentVersion>> findAll(
            @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(
                versionService.findByDocumentId(documentId)
        );
    }
}
