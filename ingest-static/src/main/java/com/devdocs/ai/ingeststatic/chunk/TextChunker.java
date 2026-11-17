
package com.devdocs.ai.ingeststatic.chunk;

import com.devdocs.ai.ingeststatic.dto.PdfPageText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Component
public class TextChunker {

    private final int chunkSize;
    private final int chunkOverlap;

    public TextChunker(
            @Value("${app.ingestion.chunk-size:1000}") int chunkSize,
            @Value("${app.ingestion.chunk-overlap:200}") int chunkOverlap
    ) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "Chunk size must be greater than zero."
            );
        }

        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "Chunk overlap must be non-negative and smaller than chunk size."
            );
        }

        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<TextChunk> chunkPages(List<PdfPageText> pages) {
        Objects.requireNonNull(pages, "Pages must not be null.");

        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (PdfPageText page : pages) {
            Objects.requireNonNull(page, "Page must not be null.");

            String text = page.text();

            if (text == null || text.isBlank()) {
                continue;
            }

            int start = 0;

            while (start < text.length()) {
                int targetEnd = Math.min(start + chunkSize, text.length());
                int end = targetEnd;

                if (targetEnd < text.length()) {
                    end = findPreferredBoundary(text, start, targetEnd);
                }

                String content = text.substring(start, end);

                if (!content.isBlank()) {
                    chunks.add(new TextChunk(
                            chunkIndex++,
                            page.pageNumber(),
                            content,
                            calculateChecksum(content)
                    ));
                }

                if (end >= text.length()) {
                    break;
                }

                int desiredStart = Math.max(
                        start + 1,
                        end - chunkOverlap
                );

                start = alignToWordBoundary(
                        text,
                        desiredStart,
                        end
                );
            }
        }

        return List.copyOf(chunks);
    }

    private int findPreferredBoundary(
            String text,
            int start,
            int targetEnd
    ) {
        int minimumEnd = start + chunkSize / 2;

        // Prefer paragraph boundaries.
        int paragraphBoundary = text.lastIndexOf("\n\n", targetEnd - 2);

        if (paragraphBoundary >= minimumEnd) {
            return paragraphBoundary + 2;
        }

        // Otherwise, prefer line boundaries.
        int lineBoundary = text.lastIndexOf('\n', targetEnd - 1);

        if (lineBoundary >= minimumEnd) {
            return lineBoundary + 1;
        }

        // Otherwise, prefer word boundaries.
        int wordBoundary = text.lastIndexOf(' ', targetEnd - 1);

        if (wordBoundary >= minimumEnd) {
            return wordBoundary + 1;
        }

        // Fall back to the maximum chunk size.
        return targetEnd;
    }

    private int alignToWordBoundary(
            String text,
            int desiredStart,
            int previousEnd
    ) {
        if (desiredStart <= 0 || desiredStart >= previousEnd) {
            return desiredStart;
        }

        // Already at the beginning of whitespace.
        if (Character.isWhitespace(text.charAt(desiredStart))) {
            return skipWhitespace(text, desiredStart, previousEnd);
        }

        // Already at a word boundary.
        if (Character.isWhitespace(text.charAt(desiredStart - 1))) {
            return desiredStart;
        }

        // Move forward to the next word boundary.
        for (int i = desiredStart; i < previousEnd; i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return skipWhitespace(text, i, previousEnd);
            }
        }

        // No boundary found: preserve the original overlap.
        return desiredStart;
    }

    private int skipWhitespace(
            String text,
            int start,
            int limit
    ) {
        int position = start;

        while (position < limit
                && Character.isWhitespace(text.charAt(position))) {
            position++;
        }

        return position;
    }

    private String calculateChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    content.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable.",
                    exception
            );
        }
    }
}
