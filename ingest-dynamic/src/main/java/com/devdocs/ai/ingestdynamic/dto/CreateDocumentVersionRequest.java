
package com.devdocs.ai.ingestdynamic.dto;

public record CreateDocumentVersionRequest(
        String versionLabel,
        String checksum
) {
}
