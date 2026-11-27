
package com.devdocs.ai.ingestdynamic.extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlContentExtractorTest {

    private final HtmlContentExtractor extractor =
            new HtmlContentExtractor();

    @Test
    void shouldExtractContentFromMainAndRemoveUnwantedElements() {
        String html = """
                <html>
                <head><title>Spring Boot Docs</title></head>
                <body>
                    <header>Site Header</header>
                    <nav>Navigation Menu</nav>
                    <div id="cookie-consent-banner">Accept cookies</div>
                    <div role="dialog" aria-label="Consent preferences">
                        Consent settings
                    </div>
                    <main>
                        <h1>Spring Boot Reference</h1>
                        <p>Spring Boot simplifies Java development.</p>
                        <script>alert('test');</script>
                        <style>.hidden { display: none; }</style>
                        <footer>Page Footer</footer>
                    </main>
                </body>
                </html>
                """;

        Document document = Jsoup.parse(html);

        ExtractedPage result = extractor.extract(
                document,
                "https://example.com/docs"
        );

        assertEquals("Spring Boot Reference", result.title());
        assertEquals("https://example.com/docs", result.sourceUrl());
        assertTrue(result.content().contains(
                "Spring Boot simplifies Java development."
        ));
        assertFalse(result.content().contains("Navigation Menu"));
        assertFalse(result.content().contains("Page Footer"));
        assertFalse(result.content().contains("Accept cookies"));
        assertFalse(result.content().contains("Consent settings"));
        assertFalse(result.content().contains("alert"));
    }

    @Test
    void shouldExtractContentFromArticleWhenMainIsMissing() {
        String html = """
                <html>
                <head><title>Java Documentation</title></head>
                <body>
                    <article>
                        <h1>Java Reference</h1>
                        <p>Java is a programming language.</p>
                    </article>
                </body>
                </html>
                """;

        ExtractedPage result = extractor.extract(
                Jsoup.parse(html),
                "https://example.com/java"
        );

        assertEquals("Java Reference", result.title());
        assertTrue(result.content().contains(
                "Java is a programming language."
        ));
    }

    @Test
    void shouldFallbackToBodyWhenMainAndArticleAreMissing() {
        String html = """
                <html>
                <head><title>Documentation</title></head>
                <body>
                    <h1>General Documentation</h1>
                    <p>This is the documentation content.</p>
                </body>
                </html>
                """;

        ExtractedPage result = extractor.extract(
                Jsoup.parse(html),
                "https://example.com/general"
        );

        assertEquals("General Documentation", result.title());
        assertTrue(result.content().contains(
                "This is the documentation content."
        ));
    }

    @Test
    void shouldRejectNullDocument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(
                        null,
                        "https://example.com/docs"
                )
        );
    }

    @Test
    void shouldRejectBlankSourceUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(
                        Jsoup.parse("<h1>Documentation</h1><p>Content</p>"),
                        " "
                )
        );
    }

    @Test
    void shouldRejectDocumentWithoutTitle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(
                        Jsoup.parse("<main><p>Some content</p></main>"),
                        "https://example.com/docs"
                )
        );
    }

    @Test
    void shouldRejectDocumentWithoutContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(
                        Jsoup.parse("<h1>Documentation</h1><main></main>"),
                        "https://example.com/docs"
                )
        );
    }

    @Test
    void shouldRejectPageWhenCleanupRemovesAllBodyContent() {
        String html = """
                <html>
                  <head><title>Navigation-only page</title></head>
                  <body>
                    <nav>Home Documentation Downloads</nav>
                    <footer>Copyright 2026</footer>
                    <div id="cookie-banner">Accept cookies</div>
                  </body>
                </html>
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(
                        Jsoup.parse(html),
                        "https://example.com/navigation-only"
                )
        );
    }
}
