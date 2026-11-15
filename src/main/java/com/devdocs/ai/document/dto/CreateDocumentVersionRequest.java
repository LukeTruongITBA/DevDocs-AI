
package com.devdocs.ai.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDocumentVersionRequest(

        @NotBlank(message = "Version label is required")
        @Size(max = 100, message = "Version label must not exceed 100 characters")
        String versionLabel,

        @NotBlank(message = "Checksum is required")
        @Pattern(
                regexp = "(?i)[a-f0-9]{64}",
                message = "Checksum must be a valid SHA-256 hex string"
        )
        String checksum

) {
}
