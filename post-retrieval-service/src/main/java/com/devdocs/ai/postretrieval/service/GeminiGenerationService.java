package com.devdocs.ai.postretrieval.service;

import com.devdocs.ai.postretrieval.client.GeminiClient;
import com.devdocs.ai.postretrieval.dto.GeminiInteractionResponse;
import org.springframework.stereotype.Service;

@Service
public class GeminiGenerationService {

    private final GeminiClient geminiClient;

    public GeminiGenerationService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    public String generateAnswer(String prompt) {
        GeminiInteractionResponse response = geminiClient.generate(prompt);

        if (response == null || response.steps() == null) {
            throw new IllegalStateException(
                    "Gemini returned an empty response."
            );
        }

        StringBuilder answer = new StringBuilder();

        for (GeminiInteractionResponse.Step step : response.steps()) {
            if (!"model_output".equals(step.type()) || step.content() == null) {
                continue;
            }

            for (GeminiInteractionResponse.Content content : step.content()) {
                if ("text".equals(content.type())
                        && content.text() != null
                        && !content.text().isBlank()) {
                    if (!answer.isEmpty()) {
                        answer.append("\n");
                    }
                    answer.append(content.text());
                }
            }
        }

        if (answer.isEmpty()) {
            throw new IllegalStateException(
                    "Gemini response does not contain generated text."
            );
        }

        return answer.toString();
    }
}