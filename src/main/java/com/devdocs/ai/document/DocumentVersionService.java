
package com.devdocs.ai.document;

import com.devdocs.ai.document.dto.CreateDocumentVersionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class DocumentVersionService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;

    public DocumentVersionService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional
    public DocumentVersion create(
            Long documentId,
            CreateDocumentVersionRequest request
    ) {
        try {
            documentRepository.findById(documentId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found: " + documentId
            );
        }

        versionRepository.clearCurrentVersion(documentId);

        String normalizedChecksum =
                request.checksum().toLowerCase(Locale.ROOT);

        return versionRepository.save(
                documentId,
                request.versionLabel(),
                normalizedChecksum
        );
    }

    public List<DocumentVersion> findByDocumentId(Long documentId) {
        try {
            documentRepository.findById(documentId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found: " + documentId
            );
        }

        return versionRepository.findByDocumentId(documentId);
    }
}
