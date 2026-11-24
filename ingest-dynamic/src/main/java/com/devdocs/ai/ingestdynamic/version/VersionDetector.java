
package com.devdocs.ai.ingestdynamic.version;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.devdocs.ai.ingestdynamic.version.VersionDetectionResult.DetectionSource;

@Component
public class VersionDetector {

    private static final Pattern VERSION_IN_PATH = Pattern.compile(
            "(?:^|/)(\\d+\\.\\d+(?:\\.\\d+)?)(?:/|$)"
    );

    private static final List<Pattern> HTML_VERSION_PATTERNS = List.of(
            Pattern.compile(
                    "(?i)Spring\\s+Boot\\s+(\\d+\\.\\d+(?:\\.\\d+)?)"
            ),
            Pattern.compile(
                    "(?i)version\\s*[:=]?\\s*[v]?([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)"
            ),
            Pattern.compile(
                    "(?i)documentation\\s+for\\s+([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)"
            )
    );

    private static final Pattern METADATA_VERSION_PATTERN =
            Pattern.compile(
                    "^\\s*[vV]?(\\d+\\.\\d+(?:\\.\\d+)?)\\s*$"
            );

    public VersionDetectionResult detect(
            String sourceUrl,
            Document document,
            String explicitVersion
    ) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Source URL must not be blank."
            );
        }

        String urlVersion = detectFromUrl(sourceUrl);

        if (urlVersion != null) {
            String htmlHeaderVersion =
                    detectFromHtmlMetadataAndTitle(document);

            validateUrlAndHtmlVersion(
                    urlVersion,
                    htmlHeaderVersion
            );

            validateExplicitVersion(urlVersion, explicitVersion);

            return new VersionDetectionResult(
                    urlVersion,
                    DetectionSource.URL
            );
        }

        String htmlVersion = detectFromHtml(document);

        if (htmlVersion != null) {
            validateExplicitVersion(htmlVersion, explicitVersion);

            return new VersionDetectionResult(
                    htmlVersion,
                    DetectionSource.HTML
            );
        }

        if (explicitVersion != null && !explicitVersion.isBlank()) {
            return new VersionDetectionResult(
                    explicitVersion,
                    DetectionSource.USER_INPUT
            );
        }

        throw new IllegalArgumentException(
                "Could not determine documentation version. "
                        + "Please provide an explicit version."
        );
    }

    private String detectFromUrl(String sourceUrl) {
        URI uri;

        try {
            uri = URI.create(sourceUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Source URL is invalid.",
                    exception
            );
        }

        String path = uri.getPath();

        if (path == null) {
            return null;
        }

        Matcher matcher = VERSION_IN_PATH.matcher(path);

        return matcher.find() ? matcher.group(1) : null;
    }

    private String detectFromHtml(Document document) {
        String headerVersion =
                detectFromHtmlMetadataAndTitle(document);

        if (headerVersion != null) {
            return headerVersion;
        }

        if (document != null && document.body() != null) {
            return findVersion(document.body().text());
        }

        return null;
    }

    private String detectFromHtmlMetadataAndTitle(
            Document document
    ) {
        if (document == null) {
            return null;
        }

        // 1. Check explicit version metadata first.
        for (String selector : List.of(
                "meta[name=version]",
                "meta[name=doc-version]",
                "meta[property=og:version]"
        )) {
            for (var element : document.select(selector)) {
                Matcher matcher = METADATA_VERSION_PATTERN.matcher(
                        element.attr("content")
                );

                if (matcher.matches()) {
                    return matcher.group(1);
                }
            }
        }

        // 2. Check the page title.
        String title = document.title();

        if (title != null && !title.isBlank()) {
            return findVersion(title);
        }

        return null;
    }

    private String findVersion(String text) {
        for (Pattern pattern : HTML_VERSION_PATTERNS) {
            Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private void validateUrlAndHtmlVersion(
            String urlVersion,
            String htmlVersion
    ) {
        if (htmlVersion != null
                && !urlVersion.equals(htmlVersion)) {
            throw new IllegalArgumentException(
                    "URL version '" + urlVersion
                            + "' conflicts with detected version '"
                            + htmlVersion + "' from HTML."
            );
        }
    }

    private void validateExplicitVersion(
            String detectedVersion,
            String explicitVersion
    ) {
        if (explicitVersion == null || explicitVersion.isBlank()) {
            return;
        }

        if (!detectedVersion.equals(explicitVersion.trim())) {
            throw new IllegalArgumentException(
                    "Explicit version '" + explicitVersion
                            + "' conflicts with detected version '"
                            + detectedVersion + "'."
            );
        }
    }
}
