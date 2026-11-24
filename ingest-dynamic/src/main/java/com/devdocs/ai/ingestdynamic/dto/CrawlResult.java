
package com.devdocs.ai.ingestdynamic.dto;

import java.util.List;

public record CrawlResult(
        List<CrawledPage> pages,
        int fetchedPages,
        int duplicateContentPages,
        List<CrawlFailure> failures,
        int pendingPages,
        boolean maxPagesReached
) {
    public CrawlResult {
        if (pages == null) {
            throw new IllegalArgumentException(
                    "Crawled pages must not be null."
            );
        }

        if (failures == null) {
            throw new IllegalArgumentException(
                    "Crawl failures must not be null."
            );
        }

        if (fetchedPages < 0
                || duplicateContentPages < 0
                || pendingPages < 0) {
            throw new IllegalArgumentException(
                    "Crawl counters must not be negative."
            );
        }

        pages = List.copyOf(pages);
        failures = List.copyOf(failures);
    }

    public boolean complete() {
        return !pages.isEmpty()
                && failures.isEmpty()
                && pendingPages == 0
                && !maxPagesReached;
    }
}
