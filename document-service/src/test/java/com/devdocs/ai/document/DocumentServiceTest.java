
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
    void createShouldCallRepositoryForStaticDocument() {
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

        verify(documentRepository, never())
                .saveOrGetWebDocument(anyString(), anyString());
    }

    @Test
    void createShouldReuseExistingWebDocument() {
        CreateDocumentRequest request = new CreateDocumentRequest(
                "Spring Boot Documentation",
                "WEB",
                "https://docs.spring.io/spring-boot/"
        );

        documentService.create(request);

        verify(documentRepository).saveOrGetWebDocument(
                "Spring Boot Documentation",
                "https://docs.spring.io/spring-boot/"
        );

        verify(documentRepository, never())
                .save(anyString(), anyString(), anyString());
    }

    @Test
    void createShouldHandleLowercaseWebSourceType() {
        CreateDocumentRequest request = new CreateDocumentRequest(
                "Spring Boot Documentation",
                "web",
                "https://docs.spring.io/spring-boot/"
        );

        documentService.create(request);

        verify(documentRepository).saveOrGetWebDocument(
                "Spring Boot Documentation",
                "https://docs.spring.io/spring-boot/"
        );
    }

    @Test
    void createRejectingExistingShouldUsePlainInsertForWebDocument() {
        CreateDocumentRequest request = new CreateDocumentRequest(
                "Spring Boot Documentation",
                "WEB",
                "https://docs.spring.io/spring-boot/"
        );

        documentService.createRejectingExisting(request);

        verify(documentRepository).save(
                "Spring Boot Documentation",
                "WEB",
                "https://docs.spring.io/spring-boot/"
        );
        verify(documentRepository, never())
                .saveOrGetWebDocument(anyString(), anyString());
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
