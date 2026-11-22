
package com.devdocs.ai.ingestdynamic.dto;

public record CreateDocumentRequest(
        String title,
        String sourceType,
        String sourceUri
) {
}
