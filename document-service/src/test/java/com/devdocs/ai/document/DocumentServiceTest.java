package com.devdocs.ai.document;

import com.devdocs.ai.document.dto.CreateDocumentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void createShouldCallRepository() {
        CreateDocumentRequest request = new CreateDocumentRequest(
                "Test Document",
                "STATIC",
                "https://example.com/test"
        );

        documentService.create(request);

        verify(documentRepository).save(
                "Test Document",
                "STATIC",
                "https://example.com/test"
        );
    }

    @Test
    void findByIdShouldReturn404WhenDocumentDoesNotExist() {
        when(documentRepository.findById(999L))
                .thenThrow(new EmptyResultDataAccessException(1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> documentService.findById(999L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }
}
