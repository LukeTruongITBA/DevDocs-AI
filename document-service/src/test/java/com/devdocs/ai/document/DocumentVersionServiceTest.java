package com.devdocs.ai.document;

import com.devdocs.ai.document.dto.CreateDocumentVersionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.web.server.ResponseStatusException;

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
    void createShouldNormalizeChecksumAndSaveVersion() {
        Long documentId = 2L;
        String checksum = "A".repeat(64);

        CreateDocumentVersionRequest request =
                new CreateDocumentVersionRequest("1.0-test", checksum);

        versionService.create(documentId, request);

        verify(documentRepository).findById(documentId);
        verify(versionRepository).clearCurrentVersion(documentId);
        verify(versionRepository).save(
                documentId,
                "1.0-test",
                checksum.toLowerCase(Locale.ROOT)
        );
    }

    @Test
    void createShouldReturn404WhenDocumentDoesNotExist() {
        Long documentId = 999L;

        CreateDocumentVersionRequest request =
                new CreateDocumentVersionRequest(
                        "1.0-test",
                        "A".repeat(64)
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
