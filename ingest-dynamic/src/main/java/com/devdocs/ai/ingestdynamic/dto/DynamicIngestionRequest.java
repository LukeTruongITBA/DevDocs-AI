
package com.devdocs.ai.ingestdynamic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DynamicIngestionRequest(

        @NotBlank(message = "Website URL must not be blank.")
        @Size(max = 2048, message = "Website URL must not exceed 2048 characters.")
        String websiteUrl,

        @Size(max = 50, message = "Explicit version must not exceed 50 characters.")
        String explicitVersion

) {
}
