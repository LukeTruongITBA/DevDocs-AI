
package com.devdocs.ai.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(

        @NotBlank(message = "Document title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @NotBlank(message = "Source type is required")
        @Pattern(
                regexp = "STATIC|WEB",
                message = "Source type must be STATIC or WEB"
        )
        String sourceType,

        String sourceUri

) {
}
