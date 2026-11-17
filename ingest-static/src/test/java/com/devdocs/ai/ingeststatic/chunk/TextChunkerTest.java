
package com.devdocs.ai.ingeststatic.chunk;

import com.devdocs.ai.ingeststatic.dto.PdfPageText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkerTest {

    @Test
    void shouldSplitTextIntoChunksWithinMaximumSize() {
        TextChunker chunker = new TextChunker(10, 2);
        String text = "abcdefghij".repeat(5);

        List<TextChunk> chunks = chunker.chunkPages(
                List.of(new PdfPageText(1, text))
        );

        assertFalse(chunks.isEmpty());

        for (TextChunk chunk : chunks) {
            assertTrue(chunk.content().length() <= 10);
        }
    }

    @Test
    void shouldPreserveOverlapBetweenChunks() {
        TextChunker chunker = new TextChunker(10, 2);
        String text = "abcdefghij".repeat(5);

        List<TextChunk> chunks = chunker.chunkPages(
                List.of(new PdfPageText(1, text))
        );

        assertTrue(chunks.size() > 1);

        for (int i = 0; i < chunks.size() - 1; i++) {
            String currentContent = chunks.get(i).content();
            String nextContent = chunks.get(i + 1).content();

            String currentSuffix = currentContent.substring(
                    currentContent.length() - 2
            );

            String nextPrefix = nextContent.substring(0, 2);

            assertEquals(currentSuffix, nextPrefix);
        }
    }

    @Test
    void shouldAlignOverlapToWordBoundaries() {
        TextChunker chunker = new TextChunker(40, 12);

        String text = (
                "alpha beta gamma delta epsilon zeta "
                        + "eta theta iota kappa lambda mu "
        ).repeat(10);

        List<TextChunk> chunks = chunker.chunkPages(
                List.of(new PdfPageText(1, text))
        );

        assertTrue(chunks.size() > 1);

        List<String> validWords = List.of(
                "alpha", "beta", "gamma", "delta",
                "epsilon", "zeta", "eta", "theta",
                "iota", "kappa", "lambda", "mu"
        );

        for (int i = 1; i < chunks.size(); i++) {
            String nextContent = chunks.get(i).content().stripLeading();
            String firstWord = nextContent.split("\\s+", 2)[0];

            assertTrue(
                    validWords.contains(firstWord),
                    "Chunk starts in the middle of a word: " + firstWord
            );
        }
    }

    @Test
    void shouldKeepPageNumbersAndContinuousChunkIndexes() {
        TextChunker chunker = new TextChunker(10, 2);

        List<PdfPageText> pages = List.of(
                new PdfPageText(1, "abcdefghij".repeat(3)),
                new PdfPageText(2, "klmnopqrst".repeat(3))
        );

        List<TextChunk> chunks = chunker.chunkPages(pages);

        assertFalse(chunks.isEmpty());

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).chunkIndex());
        }

        assertTrue(chunks.stream().anyMatch(chunk -> chunk.pageNumber() == 1));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.pageNumber() == 2));
    }

    @Test
    void shouldSkipBlankPages() {
        TextChunker chunker = new TextChunker(10, 2);

        List<PdfPageText> pages = List.of(
                new PdfPageText(1, "   \n  "),
                new PdfPageText(2, "Hello world")
        );

        List<TextChunk> chunks = chunker.chunkPages(pages);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.pageNumber() == 2));
    }

    @Test
    void shouldGenerateSha256ChecksumForEachChunk() {
        TextChunker chunker = new TextChunker(100, 10);

        List<TextChunk> chunks = chunker.chunkPages(
                List.of(new PdfPageText(1, "Hello world"))
        );

        assertEquals(1, chunks.size());

        String checksum = chunks.get(0).checksum();

        assertNotNull(checksum);
        assertEquals(64, checksum.length());
        assertTrue(checksum.matches("[a-f0-9]{64}"));
    }

    @Test
    void shouldRejectInvalidChunkConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextChunker(0, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new TextChunker(10, 10)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new TextChunker(10, -1)
        );
    }
}
