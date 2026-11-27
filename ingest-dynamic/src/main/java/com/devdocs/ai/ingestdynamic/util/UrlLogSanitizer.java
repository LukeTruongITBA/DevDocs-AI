package com.devdocs.ai.ingestdynamic.util;

import java.net.URI;

public final class UrlLogSanitizer {

    private UrlLogSanitizer() {
    }

    /** Removes credentials, query parameters, and fragments before logging a URL. */
    public static String sanitize(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "<invalid-url>";
            }
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null
            ).toASCIIString();
        } catch (RuntimeException | java.net.URISyntaxException exception) {
            return "<invalid-url>";
        }
    }
}
