package com.devdocs.ai.postretrieval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CloudflareChatResponse(
        List<Choice> choices
) {
    public record Choice(
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record Message(
            String role,
            String content,
            String reasoning,
            @JsonProperty("reasoning_content") String reasoningContent
    ) {
    }
}