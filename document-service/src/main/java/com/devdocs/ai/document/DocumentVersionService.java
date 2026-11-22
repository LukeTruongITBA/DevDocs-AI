
package com.devdocs.ai.document;

import com.devdocs.ai.document.dto.CreateDocumentVersionRequest;
import org.springframework.dao.EmptyResultDataAccessException;
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
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found: " + documentId
            );
        }

        String versionLabel = request.versionLabel().trim();
        String checksum = request.checksum().toLowerCase(Locale.ROOT);

        DocumentVersion existing =
                versionRepository.findByDocumentAndLabel(
                        documentId,
                        versionLabel
                );

        // Existing version: reuse it if content is unchanged.
        if (existing != null) {

            if (existing.checksum().equals(checksum)) {
                return existing;
            }

            // Do not allow one checksum to belong to two versions.
            boolean checksumUsed =
                    versionRepository.checksumExistsForAnotherVersion(
                            documentId,
                            checksum,
                            existing.id()
                    );

            if (checksumUsed) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Checksum already belongs to another version"
                );
            }

            return versionRepository.updateChecksum(
                    existing.id(),
                    checksum
            );
        }

        // New version: prevent duplicate checksum.
        boolean checksumUsed =
                versionRepository.checksumExistsForAnotherVersion(
                        documentId,
                        checksum,
                        -1L
                );

        if (checksumUsed) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Checksum already belongs to another version"
            );
        }

        // Only a new version becomes current.
        versionRepository.clearCurrentVersion(documentId);

        return versionRepository.save(
                documentId,
                versionLabel,
                checksum
        );
    }

    public List<DocumentVersion> findByDocumentId(Long documentId) {
        try {
            documentRepository.findById(documentId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found: " + documentId
            );
        }

        return versionRepository.findByDocumentId(documentId);
    }
}
