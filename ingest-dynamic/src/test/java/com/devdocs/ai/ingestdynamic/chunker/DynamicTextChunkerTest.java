
package com.devdocs.ai.ingestdynamic.chunker;

import com.devdocs.ai.ingestdynamic.config.IngestionProperties;
import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.extractor.ExtractedPage;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicTextChunkerTest {

    private DynamicTextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new DynamicTextChunker(
                new IngestionProperties(1000, 200, 10, 20)
        );
    }

    @Test
    void shouldCreateOneChunkForShortContent() {
        CrawledPage page = createPage("Short documentation content.");

        List<TextChunk> result = chunker.chunk(page);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunkIndex()).isZero();
        assertThat(result.get(0).content())
                .isEqualTo("Short documentation content.");
    }

    @Test
    void shouldSplitLongContentIntoMultipleChunks() {
        CrawledPage page = createPage(
                "word ".repeat(500)
        );

        List<TextChunk> result = chunker.chunk(page);

        assertThat(result).hasSizeGreaterThan(1);
        assertThat(result)
                .allSatisfy(chunk ->
                        assertThat(chunk.content().length())
                                .isLessThanOrEqualTo(1000)
                );
    }

    @Test
    void shouldPreserveMetadataInEveryChunk() {
        CrawledPage page = createPage(
                "documentation ".repeat(200)
        );

        List<TextChunk> result = chunker.chunk(page);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(chunk -> {
            assertThat(chunk.sourceUrl())
                    .isEqualTo("https://docs.example.com/3.6/");
            assertThat(chunk.pageTitle())
                    .isEqualTo("Spring Boot Reference");
            assertThat(chunk.version()).isEqualTo("3.6");
        });
    }

    @Test
    void shouldAssignSequentialChunkIndexes() {
        CrawledPage page = createPage(
                "documentation ".repeat(300)
        );

        List<TextChunk> result = chunker.chunk(page);

        assertThat(result)
                .extracting(TextChunk::chunkIndex)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream
                                .range(0, result.size())
                                .boxed()
                                .toList()
                );
    }

    @Test
    void shouldRejectNullInput() {
        assertThatThrownBy(() -> chunker.chunk(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void shouldPreserveOverlappingContent() {
        String content = "A".repeat(800)
                + " "
                + "B".repeat(800)
                + " "
                + "C".repeat(800);

        List<TextChunk> result = chunker.chunk(createPage(content));

        assertThat(result).hasSizeGreaterThan(1);

        String firstChunk = result.get(0).content();
        String secondChunk = result.get(1).content();

        assertThat(firstChunk).contains("A");
        assertThat(secondChunk).contains("B");
    }

    private CrawledPage createPage(String content) {
        return new CrawledPage(
                new ExtractedPage(
                        "Spring Boot Reference",
                        "https://docs.example.com/3.6/",
                        content
                ),
                "3.6"
        );
    }
}
