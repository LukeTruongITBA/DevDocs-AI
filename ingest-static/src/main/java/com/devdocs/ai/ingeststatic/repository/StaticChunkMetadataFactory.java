package com.devdocs.ai.ingeststatic.repository;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class StaticChunkMetadataFactory {

    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");

    private StaticChunkMetadataFactory() { }

    public static Map<String, Object> create(
            DocumentVersionInfo version,
            TextChunk chunk,
            String source
    ) {
        if (version == null || version.documentId() == null || version.documentId() <= 0
                || version.versionId() == null || version.versionId() <= 0
                || version.versionLabel() == null || version.versionLabel().isBlank()
                || !"STATIC".equalsIgnoreCase(version.sourceType())
                || chunk == null || chunk.content() == null || chunk.content().isBlank()
                || chunk.pageNumber() <= 0 || chunk.chunkIndex() < 0
                || source == null || source.isBlank()
                || chunk.checksum() == null
                || !SHA256.matcher(chunk.checksum()).matches()
                || !checksumMatches(chunk)) {
            throw new IllegalArgumentException(
                    "Static chunk metadata is missing a required value."
            );
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("doc_id", version.documentId());
        metadata.put("version", version.versionLabel());
        metadata.put("source", source);
        metadata.put("source_type", "STATIC");
        metadata.put("page", chunk.pageNumber());
        metadata.put("chunk_index", chunk.chunkIndex());
        metadata.put("checksum", chunk.checksum());
        return Map.copyOf(metadata);
    }

    private static boolean checksumMatches(TextChunk chunk) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(chunk.content().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).equals(chunk.checksum());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
