
package com.devdocs.ai.ingestdynamic.service;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.dto.CrawlFailureStage;
import com.devdocs.ai.ingestdynamic.dto.CrawlResult;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.extractor.HtmlContentExtractor;
import com.devdocs.ai.ingestdynamic.fetcher.WebsiteFetcher;
import com.devdocs.ai.ingestdynamic.fetcher.GitHubDocsFetcher;
import com.devdocs.ai.ingestdynamic.exception.WebsiteFetchException;
import com.devdocs.ai.ingestdynamic.version.VersionDetector;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebsiteCrawlerTest {

    @Mock
    private WebsiteFetcher websiteFetcher;

    @Mock
    private GitHubDocsFetcher gitHubDocsFetcher;

    private WebsiteCrawler websiteCrawler;

    @BeforeEach
    void setUp() {
        websiteCrawler = new WebsiteCrawler(
                websiteFetcher,
                new HtmlContentExtractor(),
                new VersionDetector(),
                new IngestionProperties(1000, 200, 10, 20),
                gitHubDocsFetcher
        );
    }

    @Test
    void shouldCrawlStartPage() throws IOException {
        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Introduction</h1>"
                                + "<p>Documentation content.</p></main>"
                                + "</body></html>"
                ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(1);
        assertThat(result.pages().get(0).version()).isEqualTo("3.6");
        assertThat(result.pages().get(0).page().title())
                .isEqualTo("Introduction");
        assertThat(result.complete()).isTrue();
        assertThat(result.failures()).isEmpty();
        assertThat(result.fetchedPages()).isEqualTo(1);

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/");
    }

    @Test
    void shouldRejectPageThatExceedsExtractedCharacterLimit()
            throws IOException {
        websiteCrawler = new WebsiteCrawler(
                websiteFetcher,
                new HtmlContentExtractor(),
                new VersionDetector(),
                new IngestionProperties(1000, 200, 10, 20,
                        4, 10, 200),
                gitHubDocsFetcher
        );
        when(websiteFetcher.fetch("https://docs.example.com/guide/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/guide/",
                        "<html><head><title>Guide 1</title></head>"
                                + "<body><main><h1>Guide</h1>"
                                + "<p>This content exceeds the limit.</p>"
                                + "</main></body></html>"
                ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/guide/", "1"
        );

        assertThat(result.pages()).isEmpty();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().getFirst().stage())
                .isEqualTo(CrawlFailureStage.LIMIT);
        assertThat(result.complete()).isFalse();
    }

    @Test
    void shouldExtractRemotePdfAsAnInternetSource() throws Exception {
        byte[] pdf = createPdf("Remote PDF documentation content.");
        when(websiteFetcher.fetchPdf("https://files.example.org/guide.pdf"))
                .thenReturn(pdf);

        CrawlResult result = websiteCrawler.crawl(
                "https://files.example.org/guide.pdf",
                "1.0"
        );

        assertThat(result.pages()).hasSize(1);
        assertThat(result.pages().getFirst().page().title())
                .isEqualTo("guide.pdf");
        assertThat(result.pages().getFirst().page().content())
                .contains("Remote PDF documentation content.");
        assertThat(result.pages().getFirst().page().sourceUrl())
                .isEqualTo("https://files.example.org/guide.pdf");
        assertThat(result.pages().getFirst().fetchedAt()).isNotNull();
        assertThat(result.pages().getFirst().pdfPageNumber()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void shouldPreserveOneBasedPageNumbersForMultiPagePdf() throws Exception {
        byte[] pdf = createPdfPages(
                "PDF page one content.", "PDF page two content."
        );
        when(websiteFetcher.fetchPdf("https://files.example.org/multi.pdf"))
                .thenReturn(pdf);

        CrawlResult result = websiteCrawler.crawl(
                "https://files.example.org/multi.pdf", "1.0"
        );

        assertThat(result.pages()).hasSize(2);
        assertThat(result.fetchedPages()).isEqualTo(2);
        assertThat(result.pages())
                .extracting(CrawledPage::pdfPageNumber)
                .containsExactly(1, 2);
        assertThat(result.pages())
                .extracting(page -> page.page().content())
                .containsExactly("PDF page one content.", "PDF page two content.");
        assertThat(result.complete()).isTrue();
    }

    @Test
    void shouldSkipPdfPageContainingOnlyPrintedPageNumber()
            throws Exception {
        byte[] pdf = createPdfPages(
                "PDF page one content.", "2", "PDF page three content."
        );
        when(websiteFetcher.fetchPdf("https://files.example.org/footer.pdf"))
                .thenReturn(pdf);

        CrawlResult result = websiteCrawler.crawl(
                "https://files.example.org/footer.pdf", "1.0"
        );

        assertThat(result.pages()).hasSize(2);
        assertThat(result.pages())
                .extracting(CrawledPage::pdfPageNumber)
                .containsExactly(1, 3);
        assertThat(result.pages())
                .extracting(page -> page.page().content())
                .containsExactly(
                        "PDF page one content.",
                        "PDF page three content."
                );
        assertThat(result.complete()).isTrue();
    }

    @Test
    void shouldDispatchGitHubDocsDirectoryToRepositoryFetcher() throws Exception {
        String sourceUrl =
                "https://github.com/example/project/tree/main/docs";
        CrawledPage page = new CrawledPage(
                new com.devdocs.ai.ingestdynamic.extractor.ExtractedPage(
                        "guide.md", "https://raw.githubusercontent.com/example/"
                        + "project/main/docs/guide.md", "Guide content."
                ),
                "main"
        );
        when(gitHubDocsFetcher.supports(sourceUrl)).thenReturn(true);
        when(gitHubDocsFetcher.fetch(sourceUrl, null)).thenReturn(
                new GitHubDocsFetcher.Result(
                        java.util.List.of(page), java.util.List.of()
                )
        );

        CrawlResult result = websiteCrawler.crawl(sourceUrl, null);

        assertThat(result.pages()).containsExactly(page);
        assertThat(result.complete()).isTrue();
        verifyNoInteractions(websiteFetcher);
    }

    private byte[] createPdf(String text) throws IOException {
        return createPdfPages(text);
    }

    private byte[] createPdfPages(String... texts) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : texts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream =
                             new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA
                    ), 12);
                    stream.newLineAtOffset(72, 720);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    @Test
    void shouldCrawlMultiplePagesOnSameHost() throws IOException {
        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Home</h1>"
                                + "<a href='/3.6/reference/'>Reference</a>"
                                + "</main></body></html>"
                ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/reference/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/reference/",
                "<html><head><title>Spring Boot 3.6</title></head>"
                        + "<body><main><h1>Reference</h1>"
                        + "<p>Reference content.</p>"
                        + "</main></body></html>"
        ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(2);
        assertThat(result.pages())
                .extracting(page -> page.version())
                .containsOnly("3.6");
        assertThat(result.complete()).isTrue();
    }

    @Test
    void shouldIgnoreLinksToDifferentHosts() throws IOException {
        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Home</h1>"
                                + "<a href='https://other.example.com/page'>"
                                + "External</a></main></body></html>"
                ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(1);
        assertThat(result.complete()).isTrue();

        verify(websiteFetcher, never())
                .fetch("https://other.example.com/page");
    }

    @Test
    void shouldIgnoreLinksOutsideStartPath() throws IOException {
        when(websiteFetcher.fetch(
                "https://docs.spring.io/spring-boot/"
        )).thenReturn(createDocument(
                "https://docs.spring.io/spring-boot/",
                "<html><head><title>Spring Boot 4.2.0</title></head>"
                        + "<body><main><h1>Home</h1>"
                        + "<a href='/spring-boot/reference/'>"
                        + "Spring Boot Reference</a>"
                        + "<a href='/spring-framework/reference/'>"
                        + "Spring Framework Reference</a>"
                        + "<a href='/spring-data/jpa/reference/'>"
                        + "Spring Data Reference</a>"
                        + "</main></body></html>"
        ));

        when(websiteFetcher.fetch(
                "https://docs.spring.io/spring-boot/reference/"
        )).thenReturn(createDocument(
                "https://docs.spring.io/spring-boot/reference/",
                "<html><head><title>Spring Boot 4.2.0</title></head>"
                        + "<body><main><h1>Reference</h1>"
                        + "<p>Spring Boot reference content.</p>"
                        + "</main></body></html>"
        ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.spring.io/spring-boot/",
                null
        );

        assertThat(result.pages()).hasSize(2);
        assertThat(result.complete()).isTrue();

        verify(websiteFetcher, never())
                .fetch("https://docs.spring.io/spring-framework/reference/");

        verify(websiteFetcher, never())
                .fetch("https://docs.spring.io/spring-data/jpa/reference/");
    }

    @Test
    void shouldNotCrawlDuplicateUrls() throws IOException {
        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Home</h1>"
                                + "<a href='/3.6/reference/'>First</a>"
                                + "<a href='/3.6/reference/#section'>Second</a>"
                                + "</main></body></html>"
                ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/reference/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/reference/",
                "<html><head><title>Spring Boot 3.6</title></head>"
                        + "<body><main><h1>Reference</h1>"
                        + "<p>Reference content.</p>"
                        + "</main></body></html>"
        ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(2);
        assertThat(result.complete()).isTrue();

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/reference/");
    }

    @Test
    void shouldTreatIndexHtmlAsCanonicalDirectoryUrl() throws IOException {
        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Home</h1>"
                                + "<a href='/3.6/index.html'>Index</a>"
                                + "</main></body></html>"
                ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(1);
        assertThat(result.complete()).isTrue();

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/");

        verifyNoMoreInteractions(websiteFetcher);
    }

    @Test
    void shouldSkipPagesWithDuplicateExtractedContent()
            throws IOException {

        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Introduction</h1>"
                                + "<p>Same documentation content.</p>"
                                + "</main>"
                                + "<nav><a href='/3.6/alias/'>Alias</a></nav>"
                                + "</body></html>"
                ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/alias/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/alias/",
                "<html><head><title>Spring Boot 3.6</title></head>"
                        + "<body><main><h1>Introduction</h1>"
                        + "<p>Same documentation content.</p>"
                        + "</main></body></html>"
        ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(1);
        assertThat(result.pages().get(0).page().sourceUrl())
                .isEqualTo("https://docs.example.com/3.6/");
        assertThat(result.duplicateContentPages()).isEqualTo(1);
        assertThat(result.complete()).isTrue();

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/");

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/alias/");
    }

    @Test
    void shouldKeepPagesWithDifferentExtractedContent()
            throws IOException {

        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Introduction</h1>"
                                + "<p>Introduction content.</p>"
                                + "<a href='/3.6/reference/'>Reference</a>"
                                + "</main></body></html>"
                ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/reference/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/reference/",
                "<html><head><title>Spring Boot 3.6</title></head>"
                        + "<body><main><h1>Reference</h1>"
                        + "<p>Different reference content.</p>"
                        + "</main></body></html>"
        ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(2);
        assertThat(result.pages())
                .extracting(page -> page.page().title())
                .containsExactly("Introduction", "Reference");
        assertThat(result.complete()).isTrue();
    }

    @Test
    void shouldRespectMaximumPageLimit() throws IOException {
        WebsiteCrawler limitedCrawler = new WebsiteCrawler(
                websiteFetcher,
                new HtmlContentExtractor(),
                new VersionDetector(),
                new IngestionProperties(1000, 200, 1, 20)
        );

        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Home</h1>"
                                + "<a href='/3.6/reference/'>Reference</a>"
                                + "</main></body></html>"
                ));

        CrawlResult result = limitedCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(1);
        assertThat(result.pendingPages()).isEqualTo(1);
        assertThat(result.maxPagesReached()).isTrue();
        assertThat(result.complete()).isFalse();

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/");

        verify(websiteFetcher, never())
                .fetch("https://docs.example.com/3.6/reference/");
    }

    @Test
    void shouldRespectMaximumPageLimitWhenContentIsDuplicate()
            throws IOException {

        WebsiteCrawler limitedCrawler = new WebsiteCrawler(
                websiteFetcher,
                new HtmlContentExtractor(),
                new VersionDetector(),
                new IngestionProperties(1000, 200, 2, 20)
        );

        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Introduction</h1>"
                                + "<p>Same content.</p>"
                                + "</main>"
                                + "<nav>"
                                + "<a href='/3.6/alias-one/'>One</a>"
                                + "<a href='/3.6/alias-two/'>Two</a>"
                                + "</nav></body></html>"
                ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/alias-one/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/alias-one/",
                "<html><head><title>Spring Boot 3.6</title></head>"
                        + "<body><main><h1>Introduction</h1>"
                        + "<p>Same content.</p>"
                        + "</main></body></html>"
        ));

        CrawlResult result = limitedCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(1);
        assertThat(result.duplicateContentPages()).isEqualTo(1);
        assertThat(result.pendingPages()).isEqualTo(1);
        assertThat(result.maxPagesReached()).isTrue();
        assertThat(result.complete()).isFalse();

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/");

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/alias-one/");

        verify(websiteFetcher, never())
                .fetch("https://docs.example.com/3.6/alias-two/");
    }

    @Test
    void shouldRecordConflictingChildVersionAndContinueCrawling()
            throws IOException {

        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Home</h1>"
                                + "<a href='/3.6/reference/'>Reference</a>"
                                + "<a href='/3.6/guides/'>Guides</a>"
                                + "</main></body></html>"
                ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/reference/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/reference/",
                "<html><head><title>Spring Boot 3.5</title></head>"
                        + "<body><main><h1>Reference</h1>"
                        + "<p>Old reference.</p>"
                        + "</main></body></html>"
        ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/guides/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/guides/",
                "<html><head><title>Spring Boot 3.6</title></head>"
                        + "<body><main><h1>Guides</h1>"
                        + "<p>Current guides.</p>"
                        + "</main></body></html>"
        ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(2);
        assertThat(result.pages())
                .extracting(page -> page.page().title())
                .containsExactly("Home", "Guides");

        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).stage())
                .isEqualTo(CrawlFailureStage.VERSION_DETECTION);
        assertThat(result.failures().get(0).url())
                .isEqualTo("https://docs.example.com/3.6/reference/");
        assertThat(result.complete()).isFalse();

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/guides/");
    }

    @Test
    void shouldRecordFetchFailureAndContinueCrawling()
            throws IOException {

        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Home</h1>"
                                + "<a href='/3.6/unavailable/'>Unavailable</a>"
                                + "<a href='/3.6/reference/'>Reference</a>"
                                + "</main></body></html>"
                ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/unavailable/"
        )).thenThrow(new WebsiteFetchException(
                "Website returned HTTP status 503."
        ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/reference/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/reference/",
                "<html><head><title>Spring Boot 3.6</title></head>"
                        + "<body><main><h1>Reference</h1>"
                        + "<p>Reference content.</p>"
                        + "</main></body></html>"
        ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(2);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).stage())
                .isEqualTo(CrawlFailureStage.FETCH);
        assertThat(result.failures().get(0).url())
                .isEqualTo("https://docs.example.com/3.6/unavailable/");
        assertThat(result.complete()).isFalse();

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/reference/");
    }


    @Test
    void shouldRecordExtractionFailureAndContinueCrawling()
            throws IOException {

        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Home</h1>"
                                + "<a href='/3.6/broken/'>Broken</a>"
                                + "<a href='/3.6/guides/'>Guides</a>"
                                + "</main></body></html>"
                ));

        // This page has content but no h1 or title.
        // Version detection succeeds from its URL, but extraction fails.
        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/broken/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/broken/",
                "<html><body><main>"
                        + "<p>Content without a page title.</p>"
                        + "</main></body></html>"
        ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.6/guides/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.6/guides/",
                "<html><head><title>Spring Boot 3.6</title></head>"
                        + "<body><main><h1>Guides</h1>"
                        + "<p>Current guides content.</p>"
                        + "</main></body></html>"
        ));

        CrawlResult result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result.pages()).hasSize(2);
        assertThat(result.pages())
                .extracting(page -> page.page().title())
                .containsExactly("Home", "Guides");

        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).stage())
                .isEqualTo(CrawlFailureStage.EXTRACTION);
        assertThat(result.failures().get(0).url())
                .isEqualTo("https://docs.example.com/3.6/broken/");
        assertThat(result.complete()).isFalse();

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/guides/");
    }

    @Test
    void shouldFailFastWhenFetcherReportsSecurityViolation()
            throws IOException {

        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenThrow(new com.devdocs.ai.ingestdynamic.exception
                        .WebsiteSecurityException(
                        "Redirect to a different website origin is not allowed."
                ));

        assertThatThrownBy(() -> websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        ))
                .isInstanceOf(
                        com.devdocs.ai.ingestdynamic.exception
                                .WebsiteSecurityException.class
                );

        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/");
    }

    private Document createDocument(String url, String html) {
        return Jsoup.parse(html, url);
    }
}
