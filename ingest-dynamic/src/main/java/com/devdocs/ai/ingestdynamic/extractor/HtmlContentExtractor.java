
package com.devdocs.ai.ingestdynamic.extractor;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

@Component
public class HtmlContentExtractor {

    private static final String[] UNWANTED_SELECTORS = {
            "script",
            "style",
            "noscript",
            "nav",
            "footer",
            "header",
            "aside",
            "form",
            "button",
            "svg"
    };

    public ExtractedPage extract(Document document, String sourceUrl) {
        if (document == null) {
            throw new IllegalArgumentException("HTML document must not be null.");
        }

        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Source URL must not be blank.");
        }

        Document cleanedDocument = document.clone();

        for (String selector : UNWANTED_SELECTORS) {
            cleanedDocument.select(selector).remove();
        }

        String title = extractTitle(cleanedDocument);
        String content = extractContent(cleanedDocument);

        return new ExtractedPage(
                title,
                sourceUrl,
                content
        );
    }

    private String extractTitle(Document document) {
        Element titleElement = document.selectFirst("h1");

        if (titleElement == null) {
            titleElement = document.selectFirst("title");
        }

        if (titleElement == null) {
            throw new IllegalArgumentException(
                    "Could not extract a page title."
            );
        }

        String title = normalizeWhitespace(titleElement.text());

        if (title.isBlank()) {
            throw new IllegalArgumentException(
                    "Extracted page title is blank."
            );
        }

        return title;
    }

    private String extractContent(Document document) {
        Element contentElement = document.selectFirst("main");

        if (contentElement == null) {
            contentElement = document.selectFirst("article");
        }

        if (contentElement == null) {
            contentElement = document.body();
        }

        if (contentElement == null) {
            throw new IllegalArgumentException(
                    "Could not extract page content."
            );
        }

        String content = normalizeWhitespace(contentElement.text());

        if (content.isBlank()) {
            throw new IllegalArgumentException(
                    "Extracted page content is blank."
            );
        }

        return content;
    }

    private String normalizeWhitespace(String text) {
        return text == null
                ? ""
                : text.replaceAll("\\s+", " ").trim();
    }
}
