
package com.devdocs.ai.ingestdynamic.dto;

import com.devdocs.ai.ingestdynamic.extractor.ExtractedPage;

public record CrawledPage(
        ExtractedPage page,
        String version
) {
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

        version = version.trim();
    }
}
