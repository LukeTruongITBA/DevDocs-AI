package com.devdocs.ai.postretrieval.controller;

import com.devdocs.ai.postretrieval.dto.AnswerRequest;
import com.devdocs.ai.postretrieval.dto.AnswerResponse;
import com.devdocs.ai.postretrieval.service.GeminiGenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/answers")
public class AnswerController {

    private final GeminiGenerationService generationService;

    public AnswerController(
            GeminiGenerationService generationService
    ) {
        this.generationService = generationService;
    }

    @PostMapping
    public AnswerResponse generateAnswer(
            @Valid @RequestBody AnswerRequest request
    ) {
        String prompt = buildPrompt(
                request.question(),
                request.retrievedContext()
        );

        String answer = generationService.generateAnswer(prompt);

        return new AnswerResponse(answer);
    }

    private String buildPrompt(
            String question,
            java.util.List<String> retrievedContext
    ) {
        String context = String.join("\n\n", retrievedContext);

        return """
                You are DevDocs AI, an assistant for Java and Spring developers.

                Answer the user's question using only the provided documentation context.
                If the context does not contain enough information, clearly state that.
                Do not invent technical facts.

                Documentation context:
                %s

                User question:
                %s
                """.formatted(context, question);
    }
}