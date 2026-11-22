
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
                .uri("/api/documents")
                .body(request)
                .retrieve()
                .body(DocumentResponse.class);
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
