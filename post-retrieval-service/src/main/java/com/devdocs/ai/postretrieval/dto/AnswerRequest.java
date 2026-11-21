package com.devdocs.ai.postretrieval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AnswerRequest(

        @NotBlank(message = "Question must not be blank")
        String question,

        @NotNull(message = "Retrieved context must not be null")
        @NotEmpty(message = "Retrieved context must not be empty")
        List<@NotBlank(message = "Context chunks must not be blank") String> retrievedContext

) {
}