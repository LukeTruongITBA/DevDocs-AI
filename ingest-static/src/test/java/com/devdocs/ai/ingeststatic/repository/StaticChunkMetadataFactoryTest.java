package com.devdocs.ai.ingeststatic.repository;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class StaticChunkMetadataFactoryTest {

    private final DocumentVersionInfo version = new DocumentVersionInfo(
            5L, 4L, "1.0", "a".repeat(64), "handbook.pdf", "STATIC"
    );

    @Test
    void shouldCreateAllRequiredPdfMetadataFields() {
        TextChunk chunk = new TextChunk(2, 3, "A source paragraph.", checksum("A source paragraph."));

        var metadata = StaticChunkMetadataFactory.create(version, chunk, "handbook.pdf");

        assertTrue(metadata.keySet().containsAll(Set.of(
                "doc_id", "version", "source", "page", "chunk_index", "checksum"
        )));
        assertEquals(4L, metadata.get("doc_id"));
        assertEquals("1.0", metadata.get("version"));
        assertEquals("handbook.pdf", metadata.get("source"));
        assertEquals("STATIC", metadata.get("source_type"));
        assertEquals(3, metadata.get("page"));
        assertEquals(2, metadata.get("chunk_index"));
        assertEquals(checksum("A source paragraph."), metadata.get("checksum"));
    }

    @Test
    void shouldRejectInvalidPdfMetadata() {
        assertThrows(IllegalArgumentException.class, () ->
                StaticChunkMetadataFactory.create(version,
                        new TextChunk(0, 0, "Body", checksum("Body")),
                        "handbook.pdf"));
        assertThrows(IllegalArgumentException.class, () ->
                StaticChunkMetadataFactory.create(version,
                        new TextChunk(0, 1, "Body", "not-a-checksum"),
                        "handbook.pdf"));
        assertThrows(IllegalArgumentException.class, () ->
                StaticChunkMetadataFactory.create(version,
                        new TextChunk(0, 1, "Body", checksum("Body")), " "));
    }

    private String checksum(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
