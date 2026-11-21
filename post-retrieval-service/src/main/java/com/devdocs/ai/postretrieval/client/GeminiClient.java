package com.devdocs.ai.postretrieval.client;

import com.devdocs.ai.postretrieval.config.GeminiProperties;
import com.devdocs.ai.postretrieval.dto.GeminiInteractionRequest;
import com.devdocs.ai.postretrieval.dto.GeminiInteractionResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GeminiClient {

    private final RestClient restClient;
    private final GeminiProperties properties;

    public GeminiClient(GeminiProperties properties) {
        this.restClient = RestClient.builder().build();
        this.properties = properties;
    }

    public GeminiInteractionResponse generate(String prompt) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is missing. Configure GEMINI_API_KEY."
            );
        }

        GeminiInteractionRequest request = new GeminiInteractionRequest(
                properties.model(),
                prompt
        );

        return restClient.post()
                .uri(properties.apiUrl())
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiInteractionResponse.class);
    }
}