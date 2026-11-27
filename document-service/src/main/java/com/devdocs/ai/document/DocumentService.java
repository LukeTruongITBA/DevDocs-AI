
package com.devdocs.ai.document;

import com.devdocs.ai.document.dto.CreateDocumentRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Document create(CreateDocumentRequest request) {

        // Reuse an existing WEB document with the same source URI.
        if ("WEB".equalsIgnoreCase(request.sourceType())) {
            return documentRepository.saveOrGetWebDocument(
                    request.title(),
                    request.sourceUri()
            );
        }

        // Create a new STATIC document.
        return documentRepository.save(
                request.title(),
                request.sourceType(),
                request.sourceUri()
        );
    }

    /**
     * Creates a document without the WEB source URI upsert behavior. The
     * database uniqueness constraint turns a concurrent existing-source race
     * into an observable conflict instead of an update.
     */
    public Document createRejectingExisting(CreateDocumentRequest request) {
        return documentRepository.save(
                request.title(),
                request.sourceType(),
                request.sourceUri()
        );
    }

    public List<Document> findAll() {
        return documentRepository.findAll();
    }

    public Document findById(Long id) {
        try {
            return documentRepository.findById(id);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found: " + id
            );
        }
    }
}
