
package com.devdocs.ai.ingestdynamic.fetcher;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.security.UrlSecurityValidator;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebsiteFetcherTest {

    private WebsiteFetcher websiteFetcher;
    private IngestionProperties properties;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        properties = new IngestionProperties(
                1000,
                200,
                100,
                20
        );

        websiteFetcher = new WebsiteFetcher(
                properties,
                new UrlSecurityValidator()
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectNullUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> websiteFetcher.fetch(null)
        );
    }

    @Test
    void shouldRejectBlankUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> websiteFetcher.fetch(" ")
        );
    }

    @Test
    void shouldRejectInvalidUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> websiteFetcher.fetch("not-a-valid-url")
        );
    }

    @Test
    void shouldRejectUnsupportedScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> websiteFetcher.fetch("ftp://example.com/docs")
        );
    }

    @Test
    void shouldRejectUrlWithoutHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> websiteFetcher.fetch("https:///docs")
        );
    }

    @Test
    void shouldRejectEmbeddedCredentials() {
        assertThrows(
                IllegalArgumentException.class,
                () -> websiteFetcher.fetch(
                        "https://user:password@example.com/docs"
                )
        );
    }

    @Test
    void shouldFetchHtmlFromWebsite() throws IOException {
        startTestServer();

        UrlSecurityValidator validator = mock(UrlSecurityValidator.class);

        when(validator.validate(anyString()))
                .thenAnswer(invocation ->
                        URI.create(invocation.getArgument(0))
                );

        WebsiteFetcher fetcher = new WebsiteFetcher(
                properties,
                validator
        );

        Document document = fetcher.fetch(localUrl("/docs"));

        assertNotNull(document);
        assertEquals(
                "DevDocs Test Documentation",
                document.title()
        );
        assertTrue(
                document.body().text().contains(
                        "Spring Boot simplifies Java application development."
                )
        );

        verify(validator, times(1)).validate(anyString());
    }

    @Test
    void shouldFollowSameOriginRedirect() throws IOException {
        startTestServer();

        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set(
                    "Location",
                    "/docs"
            );
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        UrlSecurityValidator validator = mock(UrlSecurityValidator.class);

        when(validator.validate(anyString()))
                .thenAnswer(invocation ->
                        URI.create(invocation.getArgument(0))
                );

        WebsiteFetcher fetcher = new WebsiteFetcher(
                properties,
                validator
        );

        Document document = fetcher.fetch(localUrl("/redirect"));

        assertEquals(
                "DevDocs Test Documentation",
                document.title()
        );

        verify(validator, times(2)).validate(anyString());
    }

    @Test
    void shouldRejectRedirectToDifferentOrigin() throws IOException {
        startTestServer();

        server.createContext("/external-redirect", exchange -> {
            exchange.getResponseHeaders().set(
                    "Location",
                    "https://example.com/docs"
            );
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        UrlSecurityValidator validator = mock(UrlSecurityValidator.class);

        when(validator.validate(anyString()))
                .thenAnswer(invocation ->
                        URI.create(invocation.getArgument(0))
                );

        WebsiteFetcher fetcher = new WebsiteFetcher(
                properties,
                validator
        );

        assertThrows(
                IOException.class,
                () -> fetcher.fetch(localUrl("/external-redirect"))
        );

        // Only the initial URL is validated.
        // The external redirect is rejected before another request.
        verify(validator, times(1)).validate(anyString());
    }

    @Test
    void shouldRejectRedirectWithoutLocation() throws IOException {
        startTestServer();

        server.createContext("/missing-location", exchange -> {
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        UrlSecurityValidator validator = mock(UrlSecurityValidator.class);

        when(validator.validate(anyString()))
                .thenAnswer(invocation ->
                        URI.create(invocation.getArgument(0))
                );

        WebsiteFetcher fetcher = new WebsiteFetcher(
                properties,
                validator
        );

        assertThrows(
                IOException.class,
                () -> fetcher.fetch(localUrl("/missing-location"))
        );
    }

    @Test
    void shouldRejectTooManyRedirects() throws IOException {
        startTestServer();

        server.createContext("/loop", exchange -> {
            exchange.getResponseHeaders().set(
                    "Location",
                    "/loop"
            );
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        UrlSecurityValidator validator = mock(UrlSecurityValidator.class);

        when(validator.validate(anyString()))
                .thenAnswer(invocation ->
                        URI.create(invocation.getArgument(0))
                );

        WebsiteFetcher fetcher = new WebsiteFetcher(
                properties,
                validator
        );

        assertThrows(
                IOException.class,
                () -> fetcher.fetch(localUrl("/loop"))
        );
    }

    private void startTestServer() throws IOException {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>DevDocs Test Documentation</title>
                </head>
                <body>
                    <main>
                        <h1>Spring Boot Documentation</h1>
                        <p>Spring Boot simplifies Java application development.</p>
                    </main>
                </body>
                </html>
                """;

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/docs", exchange -> {
            byte[] response = html.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/html; charset=UTF-8"
            );

            exchange.sendResponseHeaders(200, response.length);

            try (var outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });

        server.start();
    }

    private String localUrl(String path) {
        return "http://127.0.0.1:"
                + server.getAddress().getPort()
                + path;
    }
}
