
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
import com.devdocs.ai.ingestdynamic.fetcher.GitHubDocsFetcher;
import com.devdocs.ai.ingestdynamic.version.VersionDetectionResult;
import com.devdocs.ai.ingestdynamic.version.VersionDetector;
import com.devdocs.ai.ingestdynamic.util.UrlLogSanitizer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

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
import java.time.Instant;
import java.util.Locale;

@Component
public class WebsiteCrawler {

    private static final Logger log =
            LoggerFactory.getLogger(WebsiteCrawler.class);

    private final WebsiteFetcher websiteFetcher;
    private final HtmlContentExtractor contentExtractor;
    private final VersionDetector versionDetector;
    private final IngestionProperties properties;
    private final GitHubDocsFetcher gitHubDocsFetcher;

    @Autowired
    public WebsiteCrawler(
            WebsiteFetcher websiteFetcher,
            HtmlContentExtractor contentExtractor,
            VersionDetector versionDetector,
            IngestionProperties properties,
            GitHubDocsFetcher gitHubDocsFetcher
    ) {
        this.websiteFetcher = websiteFetcher;
        this.contentExtractor = contentExtractor;
        this.versionDetector = versionDetector;
        this.properties = properties;
        this.gitHubDocsFetcher = gitHubDocsFetcher;
    }

    public WebsiteCrawler(
            WebsiteFetcher websiteFetcher,
            HtmlContentExtractor contentExtractor,
            VersionDetector versionDetector,
            IngestionProperties properties
    ) {
        this(websiteFetcher, contentExtractor, versionDetector, properties, null);
    }

    public CrawlResult crawl(
            String startUrl,
            String explicitVersion
    ) throws IOException {

        URI startUri = normalizeUrl(startUrl);

        if (gitHubDocsFetcher != null
                && gitHubDocsFetcher.supports(startUri.toString())) {
            GitHubDocsFetcher.Result repositoryResult =
                    gitHubDocsFetcher.fetch(
                            startUri.toString(),
                            explicitVersion
                    );
            log.info(
                    "Repository docs fetch completed: sourceUrl={}, "
                            + "documents={}, failures={}",
                    UrlLogSanitizer.sanitize(startUri.toString()),
                    repositoryResult.pages().size(),
                    repositoryResult.failures().size()
            );
            return new CrawlResult(
                    repositoryResult.pages(),
                    repositoryResult.pages().size(),
                    0,
                    repositoryResult.failures(),
                    0,
                    false
            );
        }

        if (startUri.getPath().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return crawlRemotePdf(startUri, explicitVersion);
        }

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
        long extractedCharacters = 0;

        log.info(
                "Starting website crawl: startUrl={}, allowedHost={}, "
                        + "allowedPathPrefix={}, maxPages={}",
                UrlLogSanitizer.sanitize(startUri.toString()),
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
                        UrlLogSanitizer.sanitize(currentUrl),
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
                        UrlLogSanitizer.sanitize(currentUrl),
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
                        UrlLogSanitizer.sanitize(currentUrl),
                        exception.getMessage()
                );

                continue;
            }

            String contentChecksum = sha256(
                    extractedPage.content()
            );

            if (extractedCharacters + extractedPage.content().length()
                    > properties.maxExtractedCharacters()) {
                failures.add(new CrawlFailure(
                        currentUrl,
                        CrawlFailureStage.LIMIT,
                        "Crawl exceeded the configured extracted-character limit."
                ));
                log.warn(
                        "Stopping crawl at extracted-character limit: url={}, "
                                + "limit={}",
                        UrlLogSanitizer.sanitize(currentUrl),
                        properties.maxExtractedCharacters()
                );
                break;
            }
            extractedCharacters += extractedPage.content().length();

            // Store only pages with unique extracted content.
            if (visitedContentChecksums.add(contentChecksum)) {
                crawledPages.add(
                        new CrawledPage(
                                extractedPage,
                                websiteVersion,
                                Instant.now()
                        )
                );

                log.debug(
                        "Crawl retained page: url={}, title={}, contentLength={}",
                        UrlLogSanitizer.sanitize(currentUrl),
                        extractedPage.title(),
                        extractedPage.content().length()
                );

            } else {
                duplicateContentPages++;

                log.debug(
                        "Crawl skipped duplicate content: url={}, title={}",
                        UrlLogSanitizer.sanitize(currentUrl),
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

    private CrawlResult crawlRemotePdf(
            URI sourceUri,
            String explicitVersion
    ) {
        String sourceUrl = sourceUri.toString();
        try {
            byte[] bytes = websiteFetcher.fetchPdf(sourceUrl);
            try (PDDocument document = Loader.loadPDF(bytes)) {
                String path = sourceUri.getPath();
                String title = path.substring(path.lastIndexOf('/') + 1);
                if (title.isBlank()) {
                    title = "Remote PDF";
                }
                String version = explicitVersion == null
                        || explicitVersion.isBlank()
                        ? "1"
                        : explicitVersion.trim();
                Instant fetchedAt = Instant.now();
                List<CrawledPage> pages = new ArrayList<>();
                int extractedCharacters = 0;

                for (int pageNumber = 1;
                     pageNumber <= document.getNumberOfPages();
                     pageNumber++) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartPage(pageNumber);
                    stripper.setEndPage(pageNumber);
                    String content = stripper.getText(document)
                            .replaceAll("\\s+", " ").trim();
                    // A few PDFs contain a physical page number as the only
                    // extracted text. Treat that footer artifact as blank,
                    // while retaining the physical page number on real
                    // content pages for provenance.
                    if (content.matches("\\d+")) {
                        content = "";
                    }
                    if (content.isBlank()) {
                        continue;
                    }
                    extractedCharacters += content.length();
                    if (extractedCharacters > properties.maxExtractedCharacters()) {
                        throw new IllegalArgumentException(
                                "Remote PDF exceeds the configured extracted-character limit."
                        );
                    }
                    pages.add(new CrawledPage(
                            new ExtractedPage(title, sourceUrl, content),
                            version,
                            fetchedAt,
                            pageNumber
                    ));
                }

                if (pages.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Remote PDF contains no extractable text."
                    );
                }
                log.info(
                        "Remote PDF extracted: sourceUrl={}, pages={}, "
                                + "characters={}",
                        UrlLogSanitizer.sanitize(sourceUrl),
                        document.getNumberOfPages(),
                        extractedCharacters
                );
                return new CrawlResult(
                        pages, document.getNumberOfPages(), 0, List.of(), 0, false
                );
            }
        } catch (Exception exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn(
                    "Remote PDF ingestion source failed: url={}, reason={}",
                    UrlLogSanitizer.sanitize(sourceUrl),
                    message
            );
            return new CrawlResult(
                    List.of(),
                    0,
                    0,
                    List.of(new CrawlFailure(
                            sourceUrl,
                            CrawlFailureStage.FETCH,
                            message
                    )),
                    0,
                    false
            );
        }
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
