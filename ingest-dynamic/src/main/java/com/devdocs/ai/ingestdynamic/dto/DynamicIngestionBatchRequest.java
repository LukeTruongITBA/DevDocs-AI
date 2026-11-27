package com.devdocs.ai.ingestdynamic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DynamicIngestionBatchRequest(
        @NotEmpty(message = "At least one dynamic source is required.")
        @Size(max = 100, message = "A batch cannot exceed 100 sources.")
        List<@NotNull @Valid DynamicIngestionRequest> sources
)
{
}
