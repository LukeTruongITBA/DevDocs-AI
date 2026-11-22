
package com.devdocs.ai.ingestdynamic.extractor;

public record ExtractedPage(
        String title,
        String sourceUrl,
        String content
) {
    public ExtractedPage {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Page title must not be blank.");
        }

        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Source URL must not be blank.");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Page content must not be blank.");
        }
    }
}
