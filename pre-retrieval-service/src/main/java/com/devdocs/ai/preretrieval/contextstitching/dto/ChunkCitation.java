package com.devdocs.ai.preretrieval.contextstitching.dto;

import java.util.Map;

public record ChunkCitation(
        Long chunkId,
        Integer chunkIndex,
        Integer pageNumber,
        String sourceUri,
        String documentTitle,
        String versionLabel,
        Map<String, Object> metadata
) {
}