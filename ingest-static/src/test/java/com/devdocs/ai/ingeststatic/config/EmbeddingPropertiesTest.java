package com.devdocs.ai.ingeststatic.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingPropertiesTest {

    @Test
    void shouldAcceptGeminiModelWithDatabaseCompatibleDimensions() {
        EmbeddingProperties properties = new EmbeddingProperties(
                "https://example.test/embed", "", "models/gemini-embedding-001", 1536, 64
        );
        assertEquals(1536, properties.dimensions());
    }

    @Test
    void shouldRejectDimensionsIncompatibleWithPostgresVectorColumn() {
        assertThrows(IllegalArgumentException.class, () ->
                new EmbeddingProperties(
                        "https://example.test/embed", "", "models/gemini-embedding-001", 768, 64
                ));
    }
}
