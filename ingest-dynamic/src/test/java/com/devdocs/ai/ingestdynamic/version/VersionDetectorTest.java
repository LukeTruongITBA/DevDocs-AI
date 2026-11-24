
package com.devdocs.ai.ingestdynamic.version;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionDetectorTest {

    private final VersionDetector detector = new VersionDetector();

    @Test
    void shouldDetectVersionFromUrl() {
        VersionDetectionResult result = detector.detect(
                "https://docs.spring.io/spring-boot/3.5/reference/",
                Jsoup.parse("<h1>Spring Boot Reference</h1>"),
                null
        );

        assertEquals("3.5", result.version());
        assertEquals(
                VersionDetectionResult.DetectionSource.URL,
                result.source()
        );
    }

    @Test
    void shouldDetectVersion36FromUrl() {
        VersionDetectionResult result = detector.detect(
                "https://docs.spring.io/spring-boot/3.6/reference/",
                null,
                null
        );

        assertEquals("3.6", result.version());
        assertEquals(
                VersionDetectionResult.DetectionSource.URL,
                result.source()
        );
    }

    @Test
    void shouldDetectVersionFromHtmlMetadata() {
        String html = """
                <html>
                <head>
                    <meta name="version" content="3.5">
                    <title>Spring Boot Reference</title>
                </head>
                <body>
                    <h1>Documentation</h1>
                </body>
                </html>
                """;

        VersionDetectionResult result = detector.detect(
                "https://docs.example.com/reference/",
                Jsoup.parse(html),
                null
        );

        assertEquals("3.5", result.version());
        assertEquals(
                VersionDetectionResult.DetectionSource.HTML,
                result.source()
        );
    }

    @Test
    void shouldDetectVersionFromHtmlTitle() {
        String html = """
                <html>
                <head>
                    <title>Spring Boot 3.6 Reference Documentation</title>
                </head>
                <body>
                    <h1>Reference Documentation</h1>
                </body>
                </html>
                """;

        VersionDetectionResult result = detector.detect(
                "https://docs.example.com/reference/",
                Jsoup.parse(html),
                null
        );

        assertEquals("3.6", result.version());
        assertEquals(
                VersionDetectionResult.DetectionSource.HTML,
                result.source()
        );
    }

    @Test
    void shouldDetectVersionFromBodyText() {
        String html = """
                <html>
                <head><title>Reference Documentation</title></head>
                <body>
                    <h1>Spring Boot 3.5</h1>
                    <p>Reference documentation.</p>
                </body>
                </html>
                """;

        VersionDetectionResult result = detector.detect(
                "https://docs.example.com/reference/",
                Jsoup.parse(html),
                null
        );

        assertEquals("3.5", result.version());
        assertEquals(
                VersionDetectionResult.DetectionSource.HTML,
                result.source()
        );
    }

    @Test
    void shouldRejectConflictingUrlAndHtmlTitleVersion() {
        String html = """
                <html>
                <head>
                    <title>Spring Boot 3.5 Reference Documentation</title>
                </head>
                <body>
                    <h1>Reference Documentation</h1>
                </body>
                </html>
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(
                        "https://docs.spring.io/spring-boot/3.6/reference/",
                        Jsoup.parse(html),
                        null
                )
        );
    }

    @Test
    void shouldNotRejectDifferentVersionMentionedOnlyInBody() {
        String html = """
                <html>
                <head>
                    <title>Spring Boot 3.6 Reference Documentation</title>
                </head>
                <body>
                    <h1>Reference Documentation</h1>
                    <p>Migration information from Spring Boot 3.5.</p>
                </body>
                </html>
                """;

        VersionDetectionResult result = detector.detect(
                "https://docs.spring.io/spring-boot/3.6/reference/",
                Jsoup.parse(html),
                null
        );

        assertEquals("3.6", result.version());
        assertEquals(
                VersionDetectionResult.DetectionSource.URL,
                result.source()
        );
    }

    @Test
    void shouldUseExplicitVersionWhenNoVersionIsDetected() {
        VersionDetectionResult result = detector.detect(
                "https://docs.example.com/reference/",
                Jsoup.parse("<h1>Reference Documentation</h1>"),
                "3.5"
        );

        assertEquals("3.5", result.version());
        assertEquals(
                VersionDetectionResult.DetectionSource.USER_INPUT,
                result.source()
        );
    }

    @Test
    void shouldRejectConflictingExplicitVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(
                        "https://docs.spring.io/spring-boot/3.5/reference/",
                        null,
                        "3.6"
                )
        );
    }

    @Test
    void shouldRejectUnknownVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(
                        "https://docs.example.com/reference/",
                        Jsoup.parse("<h1>Reference Documentation</h1>"),
                        null
                )
        );
    }
}
