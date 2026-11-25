package com.devdocs.ai.postretrieval.client;

import com.devdocs.ai.postretrieval.config.GeminiProperties;
import com.devdocs.ai.postretrieval.dto.GeminiInteractionRequest;
import com.devdocs.ai.postretrieval.dto.GeminiInteractionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GeminiClient {

    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;
    private final GeminiProperties properties;

    public GeminiClient(
            GeminiProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this.restClient = restClientBuilder.build();
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

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return restClient.post()
                        .uri(properties.apiUrl())
                        .header("x-goog-api-key", properties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(GeminiInteractionResponse.class);

            } catch (HttpServerErrorException.ServiceUnavailable exception) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Gemini is temporarily overloaded. Please try again shortly.",
                            exception
                    );
                }

                long delayMillis = attempt * 1000L;

                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();

                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "The Gemini retry was interrupted. Please try again shortly.",
                            interruptedException
                    );
                }
            }
        }

        throw new IllegalStateException("Gemini request ended unexpectedly.");
    }
}
