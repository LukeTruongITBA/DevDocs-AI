package com.devdocs.ai.postretrieval.service;

import com.devdocs.ai.postretrieval.client.CloudflareClient;
import com.devdocs.ai.postretrieval.dto.CloudflareChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CloudflareGenerationService {

    private final CloudflareClient cloudflareClient;

    public CloudflareGenerationService(CloudflareClient cloudflareClient) {
        this.cloudflareClient = cloudflareClient;
    }

    public String generateAnswer(String prompt) {
        CloudflareChatResponse response = cloudflareClient.generate(prompt);

        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare returned an empty response."
            );
        }

        CloudflareChatResponse.Choice choice = response.choices().get(0);

        if (choice.message() == null
                || choice.message().content() == null
                || choice.message().content().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare did not return a final answer in message.content."
            );
        }

        return choice.message().content().trim();
    }
}