package com.devdocs.ai.ingeststatic.chunk;

import java.util.List;

public record ChunkEmbedding(
        int chunkIndex,
        List<Double> vector
) {
}