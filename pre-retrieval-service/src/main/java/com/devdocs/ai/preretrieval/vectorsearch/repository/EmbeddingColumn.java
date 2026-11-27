package com.devdocs.ai.preretrieval.vectorsearch.repository;

import java.util.Optional;

/**
 * Vector columns on {@code chunks}. The SQL column name is a compile-time
 * constant; it is never derived from request input.
 */
public enum EmbeddingColumn {

    /** Gemini gemini-embedding-001, V1 {@code chunks.embedding VECTOR(1536)}. */
    GEMINI("embedding", 1536),

    /** Ollama nomic-embed-text, V4 {@code chunks.embedding_ollama VECTOR(768)}. */
    OLLAMA("embedding_ollama", 768);

    private final String columnName;
    private final int dimensions;

    EmbeddingColumn(String columnName, int dimensions) {
        this.columnName = columnName;
        this.dimensions = dimensions;
    }

    public String columnName() {
        return columnName;
    }

    public int dimensions() {
        return dimensions;
    }

    public static Optional<EmbeddingColumn> forDimensions(int dimensions) {
        for (EmbeddingColumn column : values()) {
            if (column.dimensions == dimensions) {
                return Optional.of(column);
            }
        }
        return Optional.empty();
    }
}
