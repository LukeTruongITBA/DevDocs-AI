
package com.devdocs.ai.ingestdynamic.dto;

public record TextChunk(
        int chunkIndex,
        String content,
        String sourceUrl,
        String pageTitle,
        String version
) {
    public TextChunk {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException(
                    "Chunk index must not be negative."
            );
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Chunk content must not be blank."
            );
        }

        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Source URL must not be blank."
            );
        }

        if (pageTitle == null || pageTitle.isBlank()) {
            throw new IllegalArgumentException(
                    "Page title must not be blank."
            );
        }

        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(
                    "Version must not be blank."
            );
        }

        content = content.trim();
        sourceUrl = sourceUrl.trim();
        pageTitle = pageTitle.trim();
        version = version.trim();
    }
}
