package com.devdocs.ai.ingestdynamic.fetcher;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.dto.CrawlFailure;
import com.devdocs.ai.ingestdynamic.dto.CrawlFailureStage;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.extractor.ExtractedPage;
import com.devdocs.ai.ingestdynamic.security.UrlSecurityValidator;
import com.devdocs.ai.ingestdynamic.util.UrlLogSanitizer;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Fetches text documentation files from a public GitHub repository docs directory. */
@Component
public class GitHubDocsFetcher {

    private static final Logger log =
            LoggerFactory.getLogger(GitHubDocsFetcher.class);
    private static final java.util.regex.Pattern REPOSITORY_SEGMENT =
            java.util.regex.Pattern.compile("[A-Za-z0-9_.-]+");
    private static final int MAX_RESPONSE_SIZE_BYTES = 5 * 1024 * 1024;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final UrlSecurityValidator urlSecurityValidator;
    private final IngestionProperties properties;
    private final String apiBaseUrl;
    private final String rawBaseUrl;

    public GitHubDocsFetcher(
            UrlSecurityValidator urlSecurityValidator,
            IngestionProperties properties,
            ObjectMapper objectMapper,
            @Value("${app.github.api-base-url:https://api.github.com}")
            String apiBaseUrl,
            @Value("${app.github.raw-base-url:https://raw.githubusercontent.com}")
            String rawBaseUrl
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(
                        properties.requestTimeoutSeconds()
                ))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(
                properties.requestTimeoutSeconds()
        ));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.urlSecurityValidator = urlSecurityValidator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.rawBaseUrl = stripTrailingSlash(rawBaseUrl);
    }

    public boolean supports(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            String[] segments = pathSegments(uri.getPath());
            return "github.com".equalsIgnoreCase(uri.getHost())
                    && segments.length >= 5
                    && ("tree".equals(segments[2])
                    || "blob".equals(segments[2]))
                    && REPOSITORY_SEGMENT.matcher(segments[0]).matches()
                    && REPOSITORY_SEGMENT.matcher(segments[1]).matches()
                    && REPOSITORY_SEGMENT.matcher(segments[3]).matches()
                    && segments[4].equalsIgnoreCase("docs");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public Result fetch(String sourceUrl, String explicitVersion) {
        URI source = URI.create(sourceUrl);
        String[] segments = pathSegments(source.getPath());
        String owner = segments[0];
        String repository = segments[1];
        String branch = segments[3];
        String docsPath = String.join("/", java.util.Arrays.copyOfRange(
                segments, 4, segments.length
        ));
        List<CrawlFailure> failures = new ArrayList<>();
        List<CrawledPage> pages = new ArrayList<>();

        String treeUrl = apiBaseUrl + "/repos/" + owner + "/" + repository
                + "/git/trees/" + encodePathSegment(branch)
                + "?recursive=1";

        try {
            validateUrl(treeUrl);
            JsonNode tree = objectMapper.readTree(
                    fetchBounded(treeUrl, true)
            );

            JsonNode entries = tree == null ? null : tree.get("tree");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException(
                        "GitHub returned an invalid repository tree."
                );
            }

            List<String> documentPaths = new ArrayList<>();
            for (JsonNode entry : entries) {
                if (!"blob".equals(entry.path("type").asString())) {
                    continue;
                }
                String path = entry.path("path").asString("");
                if (isDocsFile(path, docsPath)) {
                    documentPaths.add(path);
                }
            }
            documentPaths.sort(Comparator.naturalOrder());

            String version = explicitVersion == null
                    || explicitVersion.isBlank()
                    ? branch
                    : explicitVersion.trim();
            long extractedCharacters = 0;

            for (String path : documentPaths.stream()
                    .limit(properties.maxPages()).toList()) {
                String rawUrl = rawBaseUrl + "/" + owner + "/" + repository
                        + "/" + encodePathSegment(branch) + "/"
                        + encodePath(path);
                try {
                    validateUrl(rawUrl);
                    String body = new String(
                            fetchBounded(rawUrl, false),
                            java.nio.charset.StandardCharsets.UTF_8
                    );
                    if (body == null || body.isBlank()) {
                        throw new IllegalStateException(
                                "Documentation file is empty."
                        );
                    }
                    if (path.toLowerCase(Locale.ROOT).endsWith(".html")) {
                        body = Jsoup.parse(body).text();
                    }
                    if (extractedCharacters + body.length()
                            > properties.maxExtractedCharacters()) {
                        failures.add(new CrawlFailure(
                                sourceUrl,
                                CrawlFailureStage.LIMIT,
                                "Repository docs exceeded the configured extracted-character limit."
                        ));
                        break;
                    }
                    extractedCharacters += body.length();
                    String title = path.substring(path.lastIndexOf('/') + 1);
                    pages.add(new CrawledPage(
                            new ExtractedPage(title, rawUrl, body),
                            version,
                            Instant.now()
                    ));
                } catch (RuntimeException exception) {
                    failures.add(new CrawlFailure(
                            rawUrl,
                            CrawlFailureStage.FETCH,
                            safeMessage(exception)
                    ));
                }
            }

            if (documentPaths.size() > properties.maxPages()) {
                failures.add(new CrawlFailure(
                        sourceUrl,
                        CrawlFailureStage.FETCH,
                        "Repository docs exceeded the configured file limit."
                ));
            }
        } catch (RuntimeException exception) {
            failures.add(new CrawlFailure(
                    sourceUrl,
                    CrawlFailureStage.FETCH,
                    safeMessage(exception)
            ));
        }

        for (CrawlFailure failure : failures) {
            log.warn(
                    "Repository documentation fetch failed: url={}, reason={}",
                    UrlLogSanitizer.sanitize(failure.url()), failure.message()
            );
        }

        return new Result(pages, failures);
    }

    private void validateUrl(String url) {
        urlSecurityValidator.validate(url);
    }

    private byte[] fetchBounded(String url, boolean json) {
        return restClient.get()
                .uri(url)
                .headers(headers -> {
                    headers.set("User-Agent", "DevDocs-AI");
                    if (json) {
                        headers.set("Accept", "application/vnd.github+json");
                    }
                })
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status >= 400) {
                        throw new IllegalStateException(
                                "GitHub returned HTTP " + status + "."
                        );
                    }
                    try (InputStream body = response.getBody()) {
                        byte[] bytes = body.readNBytes(
                                MAX_RESPONSE_SIZE_BYTES + 1
                        );
                        if (bytes.length > MAX_RESPONSE_SIZE_BYTES) {
                            throw new IllegalStateException(
                                    "GitHub response exceeds the 5 MiB limit."
                            );
                        }
                        return bytes;
                    } catch (IOException exception) {
                        throw new IllegalStateException(
                                "Could not read GitHub response.", exception
                        );
                    }
                });
    }

    private boolean isDocsFile(String path, String docsPath) {
        if (path == null || !(path.equals(docsPath)
                || path.startsWith(docsPath + "/"))) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".mdx")
                || lower.endsWith(".rst") || lower.endsWith(".txt")
                || lower.endsWith(".html");
    }

    private String[] pathSegments(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
    }

    private String encodePathSegment(String value) {
        return java.net.URLEncoder.encode(
                value,
                java.nio.charset.StandardCharsets.UTF_8
        ).replace("+", "%20");
    }

    private String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .map(this::encodePathSegment)
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    public record Result(
            List<CrawledPage> pages,
            List<CrawlFailure> failures
    ) {
        public Result {
            pages = List.copyOf(pages);
            failures = List.copyOf(failures);
        }
    }
}
