
package com.devdocs.ai.ingestdynamic.dto;

public record CrawlFailure(
        String url,
        CrawlFailureStage stage,
        String message
) {
    public CrawlFailure {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Failure URL must not be blank."
            );
        }

        if (stage == null) {
            throw new IllegalArgumentException(
                    "Failure stage must not be null."
            );
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Failure message must not be blank."
            );
        }

        url = url.trim();
        message = message.trim();
    }
}
