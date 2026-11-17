
package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.dto.ChunkPersistenceResult;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import com.devdocs.ai.ingeststatic.repository.ChunkRepository;
import com.devdocs.ai.ingeststatic.repository.DocumentVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class ChunkPersistenceService {

    private final DocumentVersionRepository documentVersionRepository;
    private final ChunkRepository chunkRepository;

    public ChunkPersistenceService(
            DocumentVersionRepository documentVersionRepository,
            ChunkRepository chunkRepository
    ) {
        this.documentVersionRepository = documentVersionRepository;
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public ChunkPersistenceResult persist(
            Long documentId,
            Long versionId,
            MultipartFile file,
            List<TextChunk> chunks
    ) throws IOException {

        if (documentId == null || documentId <= 0
                || versionId == null || versionId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "documentId and versionId must be positive numbers."
            );
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PDF file must not be empty."
            );
        }

        if (chunks == null || chunks.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No text chunks were extracted from the PDF."
            );
        }

        DocumentVersionInfo version = documentVersionRepository
                .findByVersionIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document version was not found for the supplied documentId and versionId."
                ));

        if (!"STATIC".equalsIgnoreCase(version.sourceType())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This ingestion service only accepts STATIC documents."
            );
        }

        String uploadedChecksum = calculateSha256(file.getBytes());

        if (version.checksum() == null
                || !uploadedChecksum.equalsIgnoreCase(version.checksum())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Uploaded PDF checksum does not match the registered document version."
            );
        }

        long existingChunkCount =
                chunkRepository.countByVersionId(version.versionId());

        if (existingChunkCount > 0) {
            if (existingChunkCount == chunks.size()) {
                return new ChunkPersistenceResult(
                        version.versionId(),
                        Math.toIntExact(existingChunkCount),
                        true
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Existing chunk count does not match the newly extracted chunk count. "
                            + "No chunks were inserted."
            );
        }

        String source = version.sourceUri() != null
                && !version.sourceUri().isBlank()
                ? version.sourceUri()
                : file.getOriginalFilename();

        int insertedCount = chunkRepository.insertAll(
                version,
                chunks,
                source
        );

        long finalCount =
                chunkRepository.countByVersionId(version.versionId());

        if (insertedCount != chunks.size()
                || finalCount != chunks.size()) {
            throw new IllegalStateException(
                    "Chunk persistence verification failed. Expected "
                            + chunks.size() + " chunks but found " + finalCount + "."
            );
        }

        return new ChunkPersistenceResult(
                version.versionId(),
                Math.toIntExact(finalCount),
                false
        );
    }

    private String calculateSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available.",
                    exception
            );
        }
    }
}
