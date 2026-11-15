
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
        Document document = documentService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(document);
    }

    @GetMapping
    public ResponseEntity<List<Document>> findAll() {
        return ResponseEntity.ok(documentService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> findById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(documentService.findById(id));
    }
}
