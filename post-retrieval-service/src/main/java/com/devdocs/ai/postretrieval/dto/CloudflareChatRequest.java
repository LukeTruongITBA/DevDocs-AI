package com.devdocs.ai.postretrieval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CloudflareChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") int maxTokens,
        double temperature
) {
    public record Message(
            String role,
            String content
    ) {
    }
}