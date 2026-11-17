
package com.devdocs.ai.ingeststatic.dto;

public record ChunkPersistenceResult(
        Long versionId,
        int chunkCount,
        boolean alreadyIngested
) {
}
