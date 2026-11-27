package com.devdocs.ai.ingestdynamic.embedding;

import com.devdocs.ai.ingestdynamic.dto.TextChunk;

import java.util.List;

/** Provider-neutral contract used by dynamic ingestion. */
public interface EmbeddingService {
    List<float[]> embed(List<TextChunk> chunks);

    default boolean persistenceReady() {
        return true;
    }

    default boolean isOllama() {
        return false;
    }
}
