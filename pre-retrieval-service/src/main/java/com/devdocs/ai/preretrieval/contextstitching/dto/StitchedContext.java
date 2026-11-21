package com.devdocs.ai.preretrieval.contextstitching.dto;

import java.util.List;

public record StitchedContext(
        Long documentId,
        Long versionId,
        String documentTitle,
        String versionLabel,
        String sourceUri,
        Integer startChunkIndex,
        Integer endChunkIndex,
        String content,
        List<ChunkCitation> citations
) {
}