
package com.devdocs.ai.ingestdynamic.dto;

import java.net.URI;
import java.time.Instant;

public record TextChunk(
        int chunkIndex,
        String content,
        String sourceUrl,
        String pageTitle,
        String version,
        Instant fetchedAt,
        Integer pdfPageNumber
) {
    public TextChunk(
            int chunkIndex,
            String content,
            String sourceUrl,
            String pageTitle,
            String version
    ) {
        this(chunkIndex, content, sourceUrl, pageTitle, version, Instant.now(), null);
    }

    public TextChunk(
            int chunkIndex,
            String content,
            String sourceUrl,
            String pageTitle,
            String version,
            Instant fetchedAt
    ) {
        this(chunkIndex, content, sourceUrl, pageTitle, version, fetchedAt, null);
    }

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

        if (!isValidSourceUrl(sourceUrl)) {
            throw new IllegalArgumentException(
                    "Source URL must be an absolute HTTP or HTTPS URL."
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

        if (fetchedAt == null) {
            throw new IllegalArgumentException(
                    "Fetch timestamp must not be null."
            );
        }

        if (pdfPageNumber != null && pdfPageNumber < 1) {
            throw new IllegalArgumentException(
                    "PDF page number must be one-based."
            );
        }

        content = content.trim();
        sourceUrl = sourceUrl.trim();
        pageTitle = pageTitle.trim();
        version = version.trim();
    }

    private static boolean isValidSourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(sourceUrl.trim());
            String scheme = uri.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
