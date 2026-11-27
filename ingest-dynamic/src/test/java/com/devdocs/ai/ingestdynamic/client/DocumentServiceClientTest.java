package com.devdocs.ai.ingestdynamic.client;

import com.devdocs.ai.ingestdynamic.dto.DocumentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DocumentServiceClientTest {

    private MockRestServiceServer server;
    private DocumentServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DocumentServiceClient(builder, "http://document-service");
    }

    @Test
    void looksUpSingleSourceThroughIndexedEndpointWithEncodedUri() {
        server.expect(requestTo("http://document-service/api/documents/web"
                        + "?sourceUri=https%3A%2F%2Fdocs.example.com%2Fa%3Fx%3D1%26y%3D2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":11,"title":"Core","sourceType":"WEB",
                          "sourceUri":"https://docs.example.com/a?x=1&y=2"}]
                        """, MediaType.APPLICATION_JSON));

        DocumentResponse document =
                client.findDocumentBySourceUri("https://docs.example.com/a?x=1&y=2");

        assertEquals(11L, document.id());
        server.verify();
    }

    @Test
    void emptyResultMeansNoExistingDocument() {
        server.expect(requestTo("http://document-service/api/documents/web"
                        + "?sourceUri=https%3A%2F%2Fnew.example.com%2F"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertNull(client.findDocumentBySourceUri("https://new.example.com/"));
        server.verify();
    }
}
