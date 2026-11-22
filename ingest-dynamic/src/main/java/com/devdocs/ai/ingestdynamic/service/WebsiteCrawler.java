
package com.devdocs.ai.ingestdynamic.service;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.extractor.ExtractedPage;
import com.devdocs.ai.ingestdynamic.extractor.HtmlContentExtractor;
import com.devdocs.ai.ingestdynamic.fetcher.WebsiteFetcher;
import com.devdocs.ai.ingestdynamic.version.VersionDetectionResult;
import com.devdocs.ai.ingestdynamic.version.VersionDetector;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@Component
public class WebsiteCrawler {

    private final WebsiteFetcher websiteFetcher;
    private final HtmlContentExtractor contentExtractor;
    private final VersionDetector versionDetector;
    private final IngestionProperties properties;

    public WebsiteCrawler(
            WebsiteFetcher websiteFetcher,
            HtmlContentExtractor contentExtractor,
            VersionDetector versionDetector,
            IngestionProperties properties
    ) {
        this.websiteFetcher = websiteFetcher;
        this.contentExtractor = contentExtractor;
        this.versionDetector = versionDetector;
        this.properties = properties;
    }

    public List<CrawledPage> crawl(
            String startUrl,
            String explicitVersion
    ) throws IOException {

        URI startUri = normalizeUrl(startUrl);
        String allowedHost = startUri.getHost();

        Queue<URI> pendingUrls = new ArrayDeque<>();
        Set<String> visitedUrls = new LinkedHashSet<>();
        List<CrawledPage> crawledPages = new ArrayList<>();

        pendingUrls.add(startUri);

        String websiteVersion = null;

        while (!pendingUrls.isEmpty()
                && crawledPages.size() < properties.maxPages()) {

            URI currentUri = pendingUrls.poll();
            String currentUrl = currentUri.toString();

            if (!visitedUrls.add(currentUrl)) {
                continue;
            }

            Document document = websiteFetcher.fetch(currentUrl);

            // Verify the final URL after redirects.
            String finalUrl = document.location();

            if (finalUrl != null && !finalUrl.isBlank()) {
                URI finalUri = normalizeUrl(finalUrl);

                if (!allowedHost.equalsIgnoreCase(finalUri.getHost())) {
                    throw new IllegalStateException(
                            "Website redirected outside the allowed host: "
                                    + finalUrl
                    );
                }

                String normalizedFinalUrl = finalUri.toString();

                // Only check visited URLs when a redirect changed the URL.
                if (!currentUrl.equals(normalizedFinalUrl)) {
                    if (!visitedUrls.add(normalizedFinalUrl)) {
                        continue;
                    }

                    currentUrl = normalizedFinalUrl;
                }
            }

            VersionDetectionResult detectionResult =
                    versionDetector.detect(
                            currentUrl,
                            document,
                            websiteVersion == null
                                    ? explicitVersion
                                    : websiteVersion
                    );

            if (websiteVersion == null) {
                websiteVersion = detectionResult.version();
            }

            ExtractedPage extractedPage =
                    contentExtractor.extract(document, currentUrl);

            crawledPages.add(
                    new CrawledPage(
                            extractedPage,
                            websiteVersion
                    )
            );

            // Discover links only after the page is accepted.
            for (Element link : document.select("a[href]")) {
                String absoluteUrl = link.absUrl("href");

                if (absoluteUrl.isBlank()) {
                    continue;
                }

                URI candidate;

                try {
                    candidate = normalizeUrl(absoluteUrl);
                } catch (IllegalArgumentException exception) {
                    continue;
                }

                if (!allowedHost.equalsIgnoreCase(candidate.getHost())) {
                    continue;
                }

                String candidateUrl = candidate.toString();

                if (!visitedUrls.contains(candidateUrl)
                        && !pendingUrls.contains(candidate)) {
                    pendingUrls.add(candidate);
                }
            }
        }

        return List.copyOf(crawledPages);
    }

    private URI normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Website URL must not be blank."
            );
        }

        URI uri;

        try {
            uri = URI.create(url.trim()).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Website URL is invalid.",
                    exception
            );
        }

        String scheme = uri.getScheme();

        if (scheme == null
                || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(
                    "Only HTTP and HTTPS URLs are supported."
            );
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "Website URL must contain a valid host."
            );
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Website URL must not contain embedded credentials."
            );
        }

        try {
            return new URI(
                    scheme.toLowerCase(),
                    null,
                    uri.getHost().toLowerCase(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            ).normalize();
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Could not normalize website URL.",
                    exception
            );
        }
    }
}
