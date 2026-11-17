package com.devdocs.ai.ingeststatic.dto;

public record DocumentVersionInfo(
        Long versionId,
        Long documentId,
        String versionLabel,
        String checksum,
        String sourceUri,
        String sourceType
) {
}