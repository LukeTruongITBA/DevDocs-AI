package com.devdocs.ai.postretrieval.client;

import com.devdocs.ai.postretrieval.config.CloudflareProperties;
import com.devdocs.ai.postretrieval.dto.CloudflareChatRequest;
import com.devdocs.ai.postretrieval.dto.CloudflareChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class CloudflareClient {

    private static final int MAX_TOKENS = 1200;
    private static final double TEMPERATURE = 0.2;

    private final RestClient restClient;
    private final CloudflareProperties properties;

    public CloudflareClient(
            CloudflareProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public CloudflareChatResponse generate(String prompt) {
        if (properties.accountId() == null || properties.accountId().isBlank()) {
            throw new IllegalStateException(
                    "Cloudflare Account ID is missing. Configure CLOUDFLARE_ACCOUNT_ID."
            );
        }

        if (properties.apiToken() == null || properties.apiToken().isBlank()) {
            throw new IllegalStateException(
                    "Cloudflare API Token is missing. Configure CLOUDFLARE_API_TOKEN."
            );
        }

        if (properties.model() == null || properties.model().isBlank()) {
            throw new IllegalStateException(
                    "Cloudflare model is missing. Configure CLOUDFLARE_LLM_MODEL."
            );
        }

        CloudflareChatRequest request = new CloudflareChatRequest(
                properties.model(),
                List.of(
                        new CloudflareChatRequest.Message(
                                "system",
                                "You are DevDocs AI, an accurate assistant for Java and Spring developers. "
                                        + "Answer directly using the documentation context and instructions provided."
                        ),
                        new CloudflareChatRequest.Message(
                                "user",
                                prompt
                        )
                ),
                MAX_TOKENS,
                TEMPERATURE
        );

        try {
            CloudflareChatResponse response = restClient.post()
                    .uri(properties.apiUrl())
                    .header("Authorization", "Bearer " + properties.apiToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CloudflareChatResponse.class);

            if (response == null
                    || response.choices() == null
                    || response.choices().isEmpty()) {
                throw new IllegalStateException(
                        "Cloudflare returned an empty response."
                );
            }

            return response;

        } catch (RestClientResponseException exception) {
            HttpStatus status = exception.getStatusCode().is4xxClientError()
                    && exception.getStatusCode().value() != 429
                    ? HttpStatus.BAD_GATEWAY
                    : HttpStatus.SERVICE_UNAVAILABLE;

            throw new ResponseStatusException(
                    status,
                    "Cloudflare AI request failed. Provider HTTP status: "
                            + exception.getStatusCode().value(),
                    exception
            );

        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudflare AI is temporarily unreachable. Please try again shortly.",
                    exception
            );
        }
    }
}