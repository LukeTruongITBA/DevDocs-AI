
package com.devdocs.ai.ingestdynamic.fetcher;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.exception.WebsiteFetchException;
import com.devdocs.ai.ingestdynamic.exception.WebsiteSecurityException;
import com.devdocs.ai.ingestdynamic.security.UrlSecurityValidator;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Component
public class WebsiteFetcher {

    private static final int MAX_RESPONSE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private final IngestionProperties properties;
    private final UrlSecurityValidator urlSecurityValidator;

    public WebsiteFetcher(
            IngestionProperties properties,
            UrlSecurityValidator urlSecurityValidator
    ) {
        this.properties = properties;
        this.urlSecurityValidator = urlSecurityValidator;
    }

    public Document fetch(String url) throws IOException {
        URI initialUri = urlSecurityValidator.validate(url);
        URI currentUri = initialUri;

        for (int redirectCount = 0; ; redirectCount++) {

            Connection.Response response;

            try {
                response = Jsoup.connect(currentUri.toString())
                        .userAgent("DevDocsAI/1.0 DocumentationBot")
                        .timeout(Math.toIntExact(
                                Duration.ofSeconds(
                                        properties.requestTimeoutSeconds()
                                ).toMillis()
                        ))
                        .maxBodySize(MAX_RESPONSE_SIZE_BYTES)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .execute();

            } catch (IOException exception) {
                throw new WebsiteFetchException(
                        "Failed to retrieve website content.",
                        exception
                );
            }

            int statusCode = response.statusCode();

            if (statusCode >= 300 && statusCode < 400) {

                if (redirectCount >= MAX_REDIRECTS) {
                    throw new WebsiteFetchException(
                            "Website exceeded the maximum redirect limit."
                    );
                }

                String location = response.header("Location");

                if (location == null || location.isBlank()) {
                    throw new WebsiteFetchException(
                            "Website returned a redirect without a Location header."
                    );
                }

                URI redirectUri;

                try {
                    redirectUri = currentUri.resolve(
                            location.trim()
                    ).normalize();

                } catch (IllegalArgumentException exception) {
                    throw new WebsiteSecurityException(
                            "Website returned an invalid redirect URL.",
                            exception
                    );
                }

                if (!isSameOrigin(initialUri, redirectUri)) {
                    throw new WebsiteSecurityException(
                            "Redirect to a different website origin is not allowed."
                    );
                }

                currentUri = urlSecurityValidator.validate(
                        redirectUri.toString()
                );

                continue;
            }

            if (statusCode >= 400) {
                throw new WebsiteFetchException(
                        "Website returned HTTP status " + statusCode + "."
                );
            }

            validateContentType(response.contentType());

            try {
                return response.parse();

            } catch (IOException exception) {
                throw new WebsiteFetchException(
                        "Failed to parse website HTML content.",
                        exception
                );
            }
        }
    }

    private void validateContentType(String contentType)
            throws WebsiteFetchException {

        if (contentType == null) {
            throw new WebsiteFetchException(
                    "Website response does not specify a content type."
            );
        }

        String normalized = contentType
                .toLowerCase(Locale.ROOT)
                .split(";")[0]
                .trim();

        if (!normalized.equals("text/html")
                && !normalized.equals("application/xhtml+xml")
                && !normalized.equals("application/xml")
                && !normalized.equals("text/xml")) {

            throw new WebsiteFetchException(
                    "Website response must contain HTML or XML content."
            );
        }
    }

    private boolean isSameOrigin(URI first, URI second) {
        if (second.getScheme() == null || second.getHost() == null) {
            return false;
        }

        String firstScheme = first.getScheme().toLowerCase(Locale.ROOT);
        String secondScheme = second.getScheme().toLowerCase(Locale.ROOT);

        String firstHost = first.getHost().toLowerCase(Locale.ROOT);
        String secondHost = second.getHost().toLowerCase(Locale.ROOT);

        return firstScheme.equals(secondScheme)
                && firstHost.equals(secondHost)
                && effectivePort(first) == effectivePort(second);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }

        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
