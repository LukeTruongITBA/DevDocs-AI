package com.devdocs.ai.postretrieval.client;

import com.devdocs.ai.postretrieval.config.GeminiProperties;
import com.devdocs.ai.postretrieval.dto.GeminiInteractionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

    private static final String API_URL =
            "https://example.test/v1beta/interactions";

    private GeminiClient geminiClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        GeminiProperties properties = new GeminiProperties(
                API_URL,
                "test-api-key",
                "gemini-test-model"
        );

        geminiClient = new GeminiClient(properties, builder);
    }

    @Test
    void generateShouldRetryAfter503AndSucceed() {
        mockServer.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        mockServer.expect(requestTo(API_URL))
                .andRespond(withSuccess("""
                        {
                          "id": "test-response-1",
                          "status": "completed",
                          "steps": []
                        }
                        """, MediaType.APPLICATION_JSON));

        GeminiInteractionResponse response =
                geminiClient.generate("Test prompt");

        assertNotNull(response);
        assertEquals("test-response-1", response.id());
        assertEquals("completed", response.status());

        mockServer.verify();
    }

    @Test
    void generateShouldReturn503AfterThreeFailedAttempts() {
        for (int attempt = 0; attempt < 3; attempt++) {
            mockServer.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> geminiClient.generate("Test prompt")
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        assertTrue(
                exception.getReason().contains("temporarily overloaded")
        );

        mockServer.verify();
    }
}
