
package com.devdocs.ai.ingestdynamic.service;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.dto.CrawlFailure;
import com.devdocs.ai.ingestdynamic.dto.CrawlFailureStage;
import com.devdocs.ai.ingestdynamic.dto.CrawlResult;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.exception.WebsiteFetchException;
import com.devdocs.ai.ingestdynamic.exception.WebsiteSecurityException;
import com.devdocs.ai.ingestdynamic.extractor.ExtractedPage;
import com.devdocs.ai.ingestdynamic.extractor.HtmlContentExtractor;
import com.devdocs.ai.ingestdynamic.fetcher.WebsiteFetcher;
import com.devdocs.ai.ingestdynamic.version.VersionDetectionResult;
import com.devdocs.ai.ingestdynamic.version.VersionDetector;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@Component
public class WebsiteCrawler {

    private static final Logger log =
            LoggerFactory.getLogger(WebsiteCrawler.class);

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

    public CrawlResult crawl(
            String startUrl,
            String explicitVersion
    ) throws IOException {

        URI startUri = normalizeUrl(startUrl);

        String allowedHost = startUri.getHost();
        String allowedPathPrefix = createAllowedPathPrefix(
                startUri.getPath()
        );

        Queue<URI> pendingUrls = new ArrayDeque<>();
        Set<String> visitedUrls = new LinkedHashSet<>();
        Set<String> visitedContentChecksums = new LinkedHashSet<>();

        List<CrawledPage> crawledPages = new ArrayList<>();
        List<CrawlFailure> failures = new ArrayList<>();

        pendingUrls.add(startUri);

        String websiteVersion = null;
        int fetchedPages = 0;
        int attemptedPages = 0;
        int duplicateContentPages = 0;

        log.info(
                "Starting website crawl: startUrl={}, allowedHost={}, "
                        + "allowedPathPrefix={}, maxPages={}",
                startUri,
                allowedHost,
                allowedPathPrefix,
                properties.maxPages()
        );

        while (!pendingUrls.isEmpty()
                && attemptedPages < properties.maxPages()) {

            URI currentUri = pendingUrls.poll();
            String currentUrl = currentUri.toString();

            if (!visitedUrls.add(currentUrl)) {
                continue;
            }

            attemptedPages++;

            Document document;

            try {
                document = websiteFetcher.fetch(currentUrl);
                fetchedPages++;

            } catch (WebsiteFetchException exception) {
                failures.add(
                        new CrawlFailure(
                                currentUrl,
                                CrawlFailureStage.FETCH,
                                exception.getMessage()
                        )
                );

                log.warn(
                        "Failed to fetch website page: url={}, reason={}",
                        currentUrl,
                        exception.getMessage()
                );

                continue;
            }

            // Verify the final URL after redirects.
            String finalUrl = document.location();

            if (finalUrl != null && !finalUrl.isBlank()) {
                URI finalUri = normalizeUrl(finalUrl);

                if (!isWithinAllowedScope(
                        finalUri,
                        allowedHost,
                        allowedPathPrefix
                )) {
                    throw new IllegalStateException(
                            "Website redirected outside the allowed "
                                    + "crawl scope: " + finalUrl
                    );
                }

                String normalizedFinalUrl = finalUri.toString();

                if (!currentUrl.equals(normalizedFinalUrl)) {
                    if (!visitedUrls.add(normalizedFinalUrl)) {
                        continue;
                    }

                    currentUrl = normalizedFinalUrl;
                }
            }

            // Discover links before version detection and extraction.
            // This allows the crawler to continue even if either step fails.
            discoverLinks(
                    document,
                    allowedHost,
                    allowedPathPrefix,
                    pendingUrls,
                    visitedUrls
            );

            VersionDetectionResult detectionResult;

            try {
                detectionResult = versionDetector.detect(
                        currentUrl,
                        document,
                        websiteVersion == null
                                ? explicitVersion
                                : websiteVersion
                );

            } catch (WebsiteSecurityException exception) {
                throw exception;

            } catch (IllegalArgumentException exception) {
                failures.add(
                        new CrawlFailure(
                                currentUrl,
                                CrawlFailureStage.VERSION_DETECTION,
                                exception.getMessage()
                        )
                );

                log.warn(
                        "Failed to detect website version: url={}, reason={}",
                        currentUrl,
                        exception.getMessage()
                );

                continue;
            }

            if (websiteVersion == null) {
                websiteVersion = detectionResult.version();
            }

            ExtractedPage extractedPage;

            try {
                extractedPage = contentExtractor.extract(
                        document,
                        currentUrl
                );

            } catch (IllegalArgumentException exception) {
                failures.add(
                        new CrawlFailure(
                                currentUrl,
                                CrawlFailureStage.EXTRACTION,
                                exception.getMessage()
                        )
                );

                log.warn(
                        "Failed to extract website page: url={}, reason={}",
                        currentUrl,
                        exception.getMessage()
                );

                continue;
            }

            String contentChecksum = sha256(
                    extractedPage.content()
            );

            // Store only pages with unique extracted content.
            if (visitedContentChecksums.add(contentChecksum)) {
                crawledPages.add(
                        new CrawledPage(
                                extractedPage,
                                websiteVersion
                        )
                );

                log.debug(
                        "Crawl retained page: url={}, title={}, contentLength={}",
                        currentUrl,
                        extractedPage.title(),
                        extractedPage.content().length()
                );

            } else {
                duplicateContentPages++;

                log.debug(
                        "Crawl skipped duplicate content: url={}, title={}",
                        currentUrl,
                        extractedPage.title()
                );
            }
        }

        int pendingPages = pendingUrls.size();

        boolean maxPagesReached =
                attemptedPages >= properties.maxPages()
                        && pendingPages > 0;

        CrawlResult result = new CrawlResult(
                crawledPages,
                fetchedPages,
                duplicateContentPages,
                failures,
                pendingPages,
                maxPagesReached
        );

        log.info(
                "Website crawl completed: attemptedUrls={}, fetchedUrls={}, "
                        + "retainedPages={}, duplicateContentPages={}, "
                        + "failedPages={}, visitedUrls={}, pendingUrls={}, "
                        + "maxPagesReached={}, complete={}",
                attemptedPages,
                fetchedPages,
                crawledPages.size(),
                duplicateContentPages,
                failures.size(),
                visitedUrls.size(),
                pendingPages,
                maxPagesReached,
                result.complete()
        );

        return result;
    }

    private void discoverLinks(
            Document document,
            String allowedHost,
            String allowedPathPrefix,
            Queue<URI> pendingUrls,
            Set<String> visitedUrls
    ) {
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

            if (!isWithinAllowedScope(
                    candidate,
                    allowedHost,
                    allowedPathPrefix
            )) {
                continue;
            }

            String candidateUrl = candidate.toString();

            if (!visitedUrls.contains(candidateUrl)
                    && !pendingUrls.contains(candidate)) {
                pendingUrls.add(candidate);
            }
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    content.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable.",
                    exception
            );
        }
    }

    private String createAllowedPathPrefix(String startPath) {
        if (startPath == null || startPath.isBlank()) {
            return "/";
        }

        if (startPath.endsWith("/")) {
            return startPath;
        }

        return startPath + "/";
    }

    private boolean isWithinAllowedScope(
            URI candidate,
            String allowedHost,
            String allowedPathPrefix
    ) {
        String candidateHost = candidate.getHost();

        if (candidateHost == null
                || !allowedHost.equalsIgnoreCase(candidateHost)) {
            return false;
        }

        String candidatePath = candidate.getPath();

        if (candidatePath == null || candidatePath.isBlank()) {
            candidatePath = "/";
        }

        String exactStartPath = allowedPathPrefix.equals("/")
                ? "/"
                : allowedPathPrefix.substring(
                0,
                allowedPathPrefix.length() - 1
        );

        return candidatePath.equals(exactStartPath)
                || candidatePath.startsWith(allowedPathPrefix);
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

        String path = uri.getPath();

        if (path == null || path.isBlank()) {
            path = "/";
        }

        // Treat a directory's index.html as its canonical URL.
        if (path.endsWith("/index.html")) {
            path = path.substring(
                    0,
                    path.length() - "index.html".length()
            );
        }

        try {
            return new URI(
                    scheme.toLowerCase(),
                    null,
                    uri.getHost().toLowerCase(),
                    uri.getPort(),
                    path,
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
