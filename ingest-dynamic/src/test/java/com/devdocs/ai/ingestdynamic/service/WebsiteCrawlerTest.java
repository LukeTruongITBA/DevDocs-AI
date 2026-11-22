
package com.devdocs.ai.ingestdynamic.service;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.extractor.HtmlContentExtractor;
import com.devdocs.ai.ingestdynamic.fetcher.WebsiteFetcher;
import com.devdocs.ai.ingestdynamic.version.VersionDetector;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebsiteCrawlerTest {

    @Mock
    private WebsiteFetcher websiteFetcher;

    private WebsiteCrawler websiteCrawler;

    @BeforeEach
    void setUp() {
        websiteCrawler = new WebsiteCrawler(
                websiteFetcher,
                new HtmlContentExtractor(),
                new VersionDetector(),
                new IngestionProperties(1000, 200, 10, 20)
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

        List<CrawledPage> result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).version()).isEqualTo("3.6");
        assertThat(result.get(0).page().title())
                .isEqualTo("Introduction");
        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/");
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

        List<CrawledPage> result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(page -> page.version())
                .containsOnly("3.6");
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

        List<CrawledPage> result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result).hasSize(1);
        verify(websiteFetcher, never())
                .fetch("https://other.example.com/page");
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

        List<CrawledPage> result = websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result).hasSize(2);
        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/reference/");
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

        List<CrawledPage> result = limitedCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        );

        assertThat(result).hasSize(1);
        verify(websiteFetcher, times(1))
                .fetch("https://docs.example.com/3.6/");
        verify(websiteFetcher, never())
                .fetch("https://docs.example.com/3.6/reference/");
    }

    @Test
    void shouldRejectConflictingChildVersion() throws IOException {
        when(websiteFetcher.fetch("https://docs.example.com/3.6/"))
                .thenReturn(createDocument(
                        "https://docs.example.com/3.6/",
                        "<html><head><title>Spring Boot 3.6</title></head>"
                                + "<body><main><h1>Home</h1>"
                                + "<a href='/3.5/reference/'>Old docs</a>"
                                + "</main></body></html>"
                ));

        when(websiteFetcher.fetch(
                "https://docs.example.com/3.5/reference/"
        )).thenReturn(createDocument(
                "https://docs.example.com/3.5/reference/",
                "<html><head><title>Spring Boot 3.5</title></head>"
                        + "<body><main><h1>Reference</h1>"
                        + "<p>Old reference.</p>"
                        + "</main></body></html>"
        ));

        assertThatThrownBy(() -> websiteCrawler.crawl(
                "https://docs.example.com/3.6/",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicts with detected version");
    }

    private Document createDocument(String url, String html) {
        return Jsoup.parse(html, url);
    }
}
