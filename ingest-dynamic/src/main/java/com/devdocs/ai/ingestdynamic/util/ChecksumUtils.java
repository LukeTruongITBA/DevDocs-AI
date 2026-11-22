
package com.devdocs.ai.ingestdynamic.util;

import com.devdocs.ai.ingestdynamic.dto.CrawledPage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class ChecksumUtils {

    private ChecksumUtils() {
    }

    /**
     * Calculates the SHA-256 checksum of a single text chunk.
     */
    public static String sha256(String content) {
        if (content == null) {
            throw new IllegalArgumentException(
                    "Content must not be null."
            );
        }

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

    /**
     * Calculates a deterministic checksum for an entire crawled website
     * snapshot. Pages are sorted by URL so crawl order does not affect
     * the result.
     */
    public static String websiteSnapshotChecksum(
            List<CrawledPage> pages
    ) {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Crawled pages must not be null or empty."
            );
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            List<CrawledPage> sortedPages = pages.stream()
                    .sorted(Comparator.comparing(
                            page -> page.page().sourceUrl()
                    ))
                    .toList();

            for (CrawledPage crawledPage : sortedPages) {
                updateField(
                        digest,
                        crawledPage.page().sourceUrl()
                );

                updateField(
                        digest,
                        crawledPage.page().title()
                );

                updateField(
                        digest,
                        crawledPage.page().content()
                );
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable.",
                    exception
            );
        }
    }

    private static void updateField(
            MessageDigest digest,
            String value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Checksum field must not be null."
            );
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        // Prefix each field with its byte length to avoid ambiguity.
        digest.update(
                ByteBuffer.allocate(Integer.BYTES)
                        .putInt(bytes.length)
                        .array()
        );

        digest.update(bytes);
    }
}
