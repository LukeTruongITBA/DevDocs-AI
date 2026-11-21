package com.devdocs.ai.postretrieval.dto;

import java.util.List;

public record GeminiInteractionResponse(
        String id,
        String status,
        List<Step> steps
) {

    public record Step(
            String type,
            List<Content> content
    ) {
    }

    public record Content(
            String type,
            String text
    ) {
    }
}