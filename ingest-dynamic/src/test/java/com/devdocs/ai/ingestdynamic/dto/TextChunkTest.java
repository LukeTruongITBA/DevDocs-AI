package com.devdocs.ai.ingestdynamic.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TextChunkTest {

    @Test
    void shouldRejectMissingSourceUrl() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunk(
                0, "content", null, "title", "1", Instant.EPOCH
        ));
    }

    @Test
    void shouldRejectMalformedSourceUrl() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunk(
                0, "content", "not a URL", "title", "1", Instant.EPOCH
        ));
    }

    @Test
    void shouldRejectNonHttpSourceUrl() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunk(
                0, "content", "ftp://docs.example.com/file", "title", "1",
                Instant.EPOCH
        ));
    }

    @Test
    void shouldRejectMissingFetchDate() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunk(
                0, "content", "https://docs.example.com", "title", "1",
                null
        ));
    }
}
