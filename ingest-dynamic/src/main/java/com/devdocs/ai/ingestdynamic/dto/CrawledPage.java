
package com.devdocs.ai.ingestdynamic.dto;

import com.devdocs.ai.ingestdynamic.extractor.ExtractedPage;
import java.time.Instant;

public record CrawledPage(
        ExtractedPage page,
        String version,
        Instant fetchedAt,
        Integer pdfPageNumber
) {
    public CrawledPage(ExtractedPage page, String version) {
        this(page, version, Instant.now(), null);
    }

    public CrawledPage(ExtractedPage page, String version, Instant fetchedAt) {
        this(page, version, fetchedAt, null);
    }

    public CrawledPage {
        if (page == null) {
            throw new IllegalArgumentException(
                    "Crawled page must not be null."
            );
        }

        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(
                    "Crawled page version must not be blank."
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

        version = version.trim();
    }
}
