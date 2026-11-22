
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

        // WEB documents: reuse an existing document
        // when the source URI already exists.
        if ("WEB".equalsIgnoreCase(request.sourceType())) {
            return documentRepository.saveOrGetWebDocument(
                    request.title(),
                    request.sourceUri()
            );
        }

        // STATIC documents: create a new document.
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
                    "Document not found with id: " + id
            );
        }
    }
}
