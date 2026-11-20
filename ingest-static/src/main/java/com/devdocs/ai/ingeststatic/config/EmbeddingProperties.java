package com.devdocs.ai.ingeststatic.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.embedding")
@Validated
public record EmbeddingProperties(

        @NotBlank
        String apiUrl,

        String apiKey,

        @NotBlank
        String model,

        @Min(1)
        @Max(3072)
        int dimensions,

        @Min(1)
        @Max(2048)
        int batchSize

) {
}