
package com.devdocs.ai.ingestdynamic.chunker;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DynamicTextChunker {

    private final IngestionProperties properties;

    public DynamicTextChunker(IngestionProperties properties) {
        this.properties = properties;
    }

    public List<TextChunk> chunk(CrawledPage crawledPage) {
        if (crawledPage == null) {
            throw new IllegalArgumentException(
                    "Crawled page must not be null."
            );
        }

        String content = crawledPage.page().content().trim();

        if (content.isBlank()) {
            throw new IllegalArgumentException(
                    "Page content must not be blank."
            );
        }

        int chunkSize = properties.chunkSize();
        int overlap = properties.chunkOverlap();

        List<TextChunk> chunks = new ArrayList<>();

        int start = 0;
        int chunkIndex = 0;

        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());

            if (end < content.length()) {
                int preferredEnd = findWhitespaceBoundary(
                        content,
                        start,
                        end
                );

                if (preferredEnd > start) {
                    end = preferredEnd;
                }
            }

            String chunkContent = content.substring(start, end).trim();

            if (!chunkContent.isBlank()) {
                chunks.add(new TextChunk(
                        chunkIndex++,
                        chunkContent,
                        crawledPage.page().sourceUrl(),
                        crawledPage.page().title(),
                        crawledPage.version()
                ));
            }

            if (end >= content.length()) {
                break;
            }

            int nextStart = end - overlap;

            // Ensure the next chunk always makes progress.
            if (nextStart <= start) {
                nextStart = end;
            }

            start = nextStart;
        }

        return List.copyOf(chunks);
    }

    private int findWhitespaceBoundary(
            String content,
            int start,
            int end
    ) {
        int minimumBoundary = start
                + (properties.chunkSize() / 2);

        for (int i = end - 1; i > minimumBoundary; i--) {
            if (Character.isWhitespace(content.charAt(i))) {
                return i;
            }
        }

        return end;
    }
}
