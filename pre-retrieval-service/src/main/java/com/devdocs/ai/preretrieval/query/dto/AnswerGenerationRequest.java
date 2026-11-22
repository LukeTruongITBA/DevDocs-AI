package com.devdocs.ai.preretrieval.query.dto;

import java.util.List;

public record AnswerGenerationRequest(
        String question,
        List<String> retrievedContext
) {
}