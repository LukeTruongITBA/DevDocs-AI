package com.devdocs.ai.ingestdynamic.fetcher;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.security.UrlSecurityValidator;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GitHubDocsFetcherTest {

    private MockWebServer server;
    private GitHubDocsFetcher fetcher;
    private UrlSecurityValidator validator;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        validator = mock(UrlSecurityValidator.class);
        when(validator.validate(anyString()))
                .thenAnswer(call -> URI.create(call.getArgument(0)));
        String baseUrl = server.url("/").toString();
        fetcher = new GitHubDocsFetcher(
                validator,
                new IngestionProperties(1000, 200, 10, 20),
                JsonMapper.builder().build(),
                baseUrl + "api",
                baseUrl + "raw"
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void fetchesRepositoryMarkdownDocumentsAndUsesBranchAsVersion()
            throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"tree":[
                          {"path":"docs/guide.md","type":"blob"},
                          {"path":"docs/image.png","type":"blob"},
                          {"path":"examples/readme.md","type":"blob"}
                        ]}
                        """));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("# Guide\\nRepository documentation content."));

        GitHubDocsFetcher.Result result = fetcher.fetch(
                "https://github.com/example/project/tree/main/docs",
                null
        );

        assertEquals(1, result.pages().size());
        assertTrue(result.failures().isEmpty());
        assertEquals("guide.md", result.pages().getFirst().page().title());
        assertEquals("main", result.pages().getFirst().version());
        assertTrue(result.pages().getFirst().page().content()
                .contains("Repository documentation content."));
        assertNotNull(result.pages().getFirst().fetchedAt());
        assertTrue(result.pages().getFirst().page().sourceUrl()
                .contains("/raw/example/project/main/docs/guide.md"));
        verify(validator, times(2)).validate(anyString());
    }

    @Test
    void recordsOneFailedDocumentAndContinuesFetchingOtherDocuments()
            throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"tree":[
                          {"path":"docs/broken.md","type":"blob"},
                          {"path":"docs/valid.md","type":"blob"}
                        ]}
                        """));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("Valid documentation."));

        GitHubDocsFetcher.Result result = fetcher.fetch(
                "https://github.com/example/project/tree/main/docs",
                "2.0"
        );

        assertEquals(1, result.pages().size());
        assertEquals(1, result.failures().size());
        assertEquals("2.0", result.pages().getFirst().version());
        assertTrue(result.failures().getFirst().url().endsWith("broken.md"));
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void supportsOnlyGitHubDocsDirectoryUrls() {
        assertTrue(fetcher.supports(
                "https://github.com/example/project/tree/main/docs"
        ));
        assertTrue(fetcher.supports(
                "https://github.com/example/project/blob/main/docs/guide.md"
        ));
        assertFalse(fetcher.supports(
                "https://github.com/example/project/tree/main/src"
        ));
        assertFalse(fetcher.supports("https://example.com/docs"));
        assertTrue(fetcher.supports(
                "https://github.com/example/project/tree/"
                        + "2862536793b3c67d9d83c33e0dd6d50a791928f8/docs"
        ));
    }

    @Test
    void rejectsRepositorySourceWhenAggregateTextLimitIsExceeded()
            throws Exception {
        String baseUrl = server.url("/").toString();
        fetcher = new GitHubDocsFetcher(
                validator,
                new IngestionProperties(1000, 200, 10, 20,
                        4, 10, 200),
                JsonMapper.builder().build(),
                baseUrl + "api",
                baseUrl + "raw"
        );
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"tree":[{"path":"docs/guide.md","type":"blob"}]}
                        """));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("This content exceeds the configured limit."));

        GitHubDocsFetcher.Result result = fetcher.fetch(
                "https://github.com/example/project/tree/main/docs", "main"
        );

        assertTrue(result.pages().isEmpty());
        assertEquals(1, result.failures().size());
        assertEquals(com.devdocs.ai.ingestdynamic.dto.CrawlFailureStage.LIMIT,
                result.failures().getFirst().stage());
    }
}
