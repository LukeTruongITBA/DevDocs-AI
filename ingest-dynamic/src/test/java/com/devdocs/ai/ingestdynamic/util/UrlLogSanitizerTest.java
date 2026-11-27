package com.devdocs.ai.ingestdynamic.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrlLogSanitizerTest {

    @Test
    void removesCredentialsQueryAndFragmentBeforeLogging() {
        assertEquals(
                "https://example.org/docs",
                UrlLogSanitizer.sanitize(
                        "https://user:secret@example.org/docs?token=hidden#part"
                )
        );
    }
}
