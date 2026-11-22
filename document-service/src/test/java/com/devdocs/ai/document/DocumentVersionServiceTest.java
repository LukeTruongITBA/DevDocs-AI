
package com.devdocs.ai.document;

import com.devdocs.ai.document.dto.CreateDocumentVersionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentVersionServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository versionRepository;

    @InjectMocks
    private DocumentVersionService versionService;

    @Test
    void createShouldSaveNewVersionAndMakeItCurrent() {
        Long documentId = 2L;
        String checksum = "A".repeat(64);
        String normalizedChecksum = checksum.toLowerCase(Locale.ROOT);

        CreateDocumentVersionRequest request =
                new CreateDocumentVersionRequest("3.6", checksum);

        when(versionRepository.findByDocumentAndLabel(documentId, "3.6"))
                .thenReturn(null);

        when(versionRepository.checksumExistsForAnotherVersion(
                documentId, normalizedChecksum, -1L
        )).thenReturn(false);

        versionService.create(documentId, request);

        verify(documentRepository).findById(documentId);
        verify(versionRepository).clearCurrentVersion(documentId);
        verify(versionRepository).save(
                documentId, "3.6", normalizedChecksum
        );
    }

    @Test
    void createShouldReuseExistingVersionWhenChecksumIsUnchanged() {
        Long documentId = 2L;
        String checksum = "a".repeat(64);

        DocumentVersion existing = new DocumentVersion(
                10L, documentId, "3.6", checksum,
                true, null, OffsetDateTime.now()
        );

        when(versionRepository.findByDocumentAndLabel(documentId, "3.6"))
                .thenReturn(existing);

        DocumentVersion result = versionService.create(
                documentId,
                new CreateDocumentVersionRequest("3.6", checksum)
        );

        assertEquals(existing, result);

        verify(versionRepository, never()).clearCurrentVersion(anyLong());
        verify(versionRepository, never()).save(
                anyLong(), anyString(), anyString()
        );
        verify(versionRepository, never()).updateChecksum(
                anyLong(), anyString()
        );
    }

    @Test
    void createShouldUpdateChecksumWhenExistingVersionChanges() {
        Long documentId = 2L;
        String oldChecksum = "a".repeat(64);
        String newChecksum = "b".repeat(64);

        DocumentVersion existing = new DocumentVersion(
                10L, documentId, "3.6", oldChecksum,
                true, null, OffsetDateTime.now()
        );

        DocumentVersion updated = new DocumentVersion(
                10L, documentId, "3.6", newChecksum,
                true, null, OffsetDateTime.now()
        );

        when(versionRepository.findByDocumentAndLabel(documentId, "3.6"))
                .thenReturn(existing);

        when(versionRepository.checksumExistsForAnotherVersion(
                documentId, newChecksum, 10L
        )).thenReturn(false);

        when(versionRepository.updateChecksum(10L, newChecksum))
                .thenReturn(updated);

        DocumentVersion result = versionService.create(
                documentId,
                new CreateDocumentVersionRequest("3.6", newChecksum)
        );

        assertEquals(newChecksum, result.checksum());
        assertEquals(10L, result.id());

        verify(versionRepository, never()).clearCurrentVersion(anyLong());
        verify(versionRepository).updateChecksum(10L, newChecksum);
    }

    @Test
    void createShouldReturn409WhenChecksumBelongsToAnotherVersion() {
        Long documentId = 2L;
        String checksum = "b".repeat(64);

        when(versionRepository.findByDocumentAndLabel(documentId, "3.7"))
                .thenReturn(null);

        when(versionRepository.checksumExistsForAnotherVersion(
                documentId, checksum, -1L
        )).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> versionService.create(
                        documentId,
                        new CreateDocumentVersionRequest("3.7", checksum)
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(versionRepository, never()).clearCurrentVersion(anyLong());
        verify(versionRepository, never()).save(
                anyLong(), anyString(), anyString()
        );
    }

    @Test
    void createShouldReturn404WhenDocumentDoesNotExist() {
        Long documentId = 999L;

        CreateDocumentVersionRequest request =
                new CreateDocumentVersionRequest(
                        "3.6", "A".repeat(64)
                );

        when(documentRepository.findById(documentId))
                .thenThrow(new EmptyResultDataAccessException(1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> versionService.create(documentId, request)
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(versionRepository);
    }

    @Test
    void findByDocumentIdShouldReturnVersions() {
        Long documentId = 2L;

        when(versionRepository.findByDocumentId(documentId))
                .thenReturn(List.of());

        List<DocumentVersion> result =
                versionService.findByDocumentId(documentId);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(documentRepository).findById(documentId);
        verify(versionRepository).findByDocumentId(documentId);
    }
}
