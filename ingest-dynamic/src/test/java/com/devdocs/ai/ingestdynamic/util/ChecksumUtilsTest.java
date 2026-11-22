
package com.devdocs.ai.ingestdynamic.util;

import com.devdocs.ai.ingestdynamic.dto.CrawledPage;
import com.devdocs.ai.ingestdynamic.extractor.ExtractedPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumUtilsTest {

    @Test
    void shouldCalculateSha256Checksum() {
        String checksum = ChecksumUtils.sha256("hello");

        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                checksum
        );
    }

    @Test
    void shouldReturn64CharacterChecksum() {
        String checksum = ChecksumUtils.sha256("Spring Boot");

        assertEquals(64, checksum.length());
    }

    @Test
    void shouldRejectNullContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChecksumUtils.sha256(null)
        );
    }

    @Test
    void shouldRejectEmptyWebsiteSnapshot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChecksumUtils.websiteSnapshotChecksum(List.of())
        );
    }

    @Test
    void shouldReturnSameChecksumRegardlessOfPageOrder() {
        CrawledPage pageA = createPage(
                "https://docs.example.com/a",
                "Page A",
                "Content A"
        );

        CrawledPage pageB = createPage(
                "https://docs.example.com/b",
                "Page B",
                "Content B"
        );

        String first = ChecksumUtils.websiteSnapshotChecksum(
                List.of(pageA, pageB)
        );

        String second = ChecksumUtils.websiteSnapshotChecksum(
                List.of(pageB, pageA)
        );

        assertEquals(first, second);
    }

    @Test
    void shouldChangeSnapshotChecksumWhenContentChanges() {
        CrawledPage original = createPage(
                "https://docs.example.com/a",
                "Page A",
                "Original content"
        );

        CrawledPage changed = createPage(
                "https://docs.example.com/a",
                "Page A",
                "Updated content"
        );

        assertNotEquals(
                ChecksumUtils.websiteSnapshotChecksum(List.of(original)),
                ChecksumUtils.websiteSnapshotChecksum(List.of(changed))
        );
    }

    private CrawledPage createPage(
            String url,
            String title,
            String content
    ) {
        return new CrawledPage(
                new ExtractedPage(title, url, content),
                "3.6"
        );
    }
}
