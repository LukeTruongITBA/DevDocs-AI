
package com.devdocs.ai.postretrieval.service;

import com.devdocs.ai.postretrieval.client.CloudflareClient;
import com.devdocs.ai.postretrieval.dto.CloudflareChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudflareGenerationServiceTest {

    private CloudflareClient cloudflareClient;
    private CloudflareGenerationService generationService;

    @BeforeEach
    void setUp() {
        cloudflareClient = mock(CloudflareClient.class);
        generationService = new CloudflareGenerationService(cloudflareClient);
    }

    @Test
    void generateAnswerShouldReturnTrimmedContent() {
        CloudflareChatResponse response = new CloudflareChatResponse(
                List.of(
                        new CloudflareChatResponse.Choice(
                                new CloudflareChatResponse.Message(
                                        "assistant",
                                        "  Dependency Injection is a design pattern.  ",
                                        null,
                                        null
                                ),
                                "stop"
                        )
                )
        );

        when(cloudflareClient.generate("Test prompt")).thenReturn(response);

        String answer = generationService.generateAnswer("Test prompt");

        assertThat(answer).isEqualTo(
                "Dependency Injection is a design pattern."
        );

        verify(cloudflareClient).generate("Test prompt");
    }

    @Test
    void generateAnswerShouldRejectEmptyChoices() {
        CloudflareChatResponse response = new CloudflareChatResponse(
                List.of()
        );

        when(cloudflareClient.generate("Test prompt")).thenReturn(response);

        assertThatThrownBy(
                () -> generationService.generateAnswer("Test prompt")
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_GATEWAY);
                });
    }

    @Test
    void generateAnswerShouldRejectBlankContent() {
        CloudflareChatResponse response = new CloudflareChatResponse(
                List.of(
                        new CloudflareChatResponse.Choice(
                                new CloudflareChatResponse.Message(
                                        "assistant",
                                        "   ",
                                        null,
                                        null
                                ),
                                "stop"
                        )
                )
        );

        when(cloudflareClient.generate("Test prompt")).thenReturn(response);

        assertThatThrownBy(
                () -> generationService.generateAnswer("Test prompt")
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_GATEWAY);
                });
    }
}
