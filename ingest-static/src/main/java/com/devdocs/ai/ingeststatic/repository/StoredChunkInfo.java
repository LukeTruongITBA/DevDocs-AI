package com.devdocs.ai.ingeststatic.repository;

public record StoredChunkInfo(
        int chunkIndex,
        String checksum,
        boolean embeddingMissing
) {
}