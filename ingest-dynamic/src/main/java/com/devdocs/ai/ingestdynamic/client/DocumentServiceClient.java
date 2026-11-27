
package com.devdocs.ai.ingestdynamic.client;

import com.devdocs.ai.ingestdynamic.dto.CreateDocumentRequest;
import com.devdocs.ai.ingestdynamic.dto.CreateDocumentVersionRequest;
import com.devdocs.ai.ingestdynamic.dto.DocumentResponse;
import com.devdocs.ai.ingestdynamic.dto.DocumentVersionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Objects;

@Component
public class DocumentServiceClient {

    private final RestClient restClient;

    public DocumentServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.document-service.base-url:http://localhost:8081}")
            String baseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public DocumentResponse createDocument(
            CreateDocumentRequest request
    ) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/documents")
                        .queryParam("rejectExisting", true)
                        .build())
                .body(request)
                .retrieve()
                .body(DocumentResponse.class);
    }

    /**
     * Reads the existing document for a source without invoking the document
     * service's upsert endpoint.
     */
    public DocumentResponse findDocumentBySourceUri(String sourceUri) {
        List<DocumentResponse> documents = restClient.get()
                .uri("/api/documents")
                .retrieve()
                .body(new ParameterizedTypeReference<
                        List<DocumentResponse>
                        >() {});

        if (documents == null) {
            return null;
        }

        return documents.stream()
                .filter(Objects::nonNull)
                .filter(document -> "WEB".equalsIgnoreCase(
                        document.sourceType()
                ))
                .filter(document -> sourceUri.equals(document.sourceUri()))
                .findFirst()
                .orElse(null);
    }

    public DocumentVersionResponse createVersion(
            Long documentId,
            CreateDocumentVersionRequest request
    ) {
        return restClient.post()
                .uri("/api/documents/{documentId}/versions", documentId)
                .body(request)
                .retrieve()
                .body(DocumentVersionResponse.class);
    }

    public List<DocumentVersionResponse> findVersions(
            Long documentId
    ) {
        List<DocumentVersionResponse> versions = restClient.get()
                .uri("/api/documents/{documentId}/versions", documentId)
                .retrieve()
                .body(new ParameterizedTypeReference<
                        List<DocumentVersionResponse>
                        >() {});

        return versions == null ? List.of() : List.copyOf(versions);
    }
}
