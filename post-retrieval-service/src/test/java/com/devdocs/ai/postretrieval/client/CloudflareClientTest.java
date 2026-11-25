
package com.devdocs.ai.postretrieval.client;

import com.devdocs.ai.postretrieval.config.CloudflareProperties;
import com.devdocs.ai.postretrieval.dto.CloudflareChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudflareClientTest {

    private static final String API_URL =
            "https://api.cloudflare.com/client/v4/accounts/test-account/ai/v1/chat/completions";

    private CloudflareClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer.bindTo(builder).build();

        CloudflareProperties properties = new CloudflareProperties(
                "test-account",
                "test-token",
                "@cf/zai-org/glm-4.7-flash"
        );

        client = new CloudflareClient(properties, builder);
    }

    @Test
    void generateShouldReturnCloudflareAnswer() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "Dependency Injection is a design pattern.",
                                "reasoning": "This field must not be shown to users."
                              },
                              "finish_reason": "stop"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        CloudflareChatResponse response = client.generate(
                "What is dependency injection?"
        );

        assertThat(response.choices()).hasSize(1);
        assertThat(response.choices().get(0).message().content())
                .isEqualTo("Dependency Injection is a design pattern.");

        mockServer.verify();
    }

    @Test
    void generateShouldMapRateLimitToServiceUnavailable() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generate("Test prompt"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });

        mockServer.verify();
    }

    @Test
    void generateShouldMapProviderServerErrorToServiceUnavailable() {
        mockServer.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.generate("Test prompt"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });

        mockServer.verify();
    }

    @Test
    void generateShouldMapProviderBadRequestToBadGateway() {
        mockServer.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.generate("Test prompt"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_GATEWAY);
                });

        mockServer.verify();
    }

    @Test
    void generateShouldRejectMissingAccountId() {
        CloudflareClient invalidClient = new CloudflareClient(
                new CloudflareProperties(
                        "",
                        "test-token",
                        "@cf/zai-org/glm-4.7-flash"
                ),
                RestClient.builder()
        );

        assertThatThrownBy(() -> invalidClient.generate("Test prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account ID is missing");
    }

    @Test
    void generateShouldRejectMissingApiToken() {
        CloudflareClient invalidClient = new CloudflareClient(
                new CloudflareProperties(
                        "test-account",
                        "",
                        "@cf/zai-org/glm-4.7-flash"
                ),
                RestClient.builder()
        );

        assertThatThrownBy(() -> invalidClient.generate("Test prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API Token is missing");
    }

    @Test
    void generateShouldRejectMissingModel() {
        CloudflareClient invalidClient = new CloudflareClient(
                new CloudflareProperties(
                        "test-account",
                        "test-token",
                        ""
                ),
                RestClient.builder()
        );

        assertThatThrownBy(() -> invalidClient.generate("Test prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model is missing");
    }

    @Test
    void generateShouldRejectEmptyChoices() {
        mockServer.expect(requestTo(API_URL))
                .andRespond(withSuccess("""
                        {
                          "choices": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate("Test prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty response");

        mockServer.verify();
    }

    @Test
    void generateShouldMapNetworkFailureToServiceUnavailable() {
        mockServer.expect(requestTo(API_URL))
                .andRespond(withException(
                        new IOException("Connection failed")
                ));

        assertThatThrownBy(() -> client.generate("Test prompt"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });

        mockServer.verify();
    }
}
