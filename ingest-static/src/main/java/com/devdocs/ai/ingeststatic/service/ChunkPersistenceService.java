package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.ChunkEmbedding;
import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.dto.ChunkPersistenceResult;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import com.devdocs.ai.ingeststatic.repository.ChunkRepository;
import com.devdocs.ai.ingeststatic.repository.DocumentVersionRepository;
import com.devdocs.ai.ingeststatic.repository.StoredChunkInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChunkPersistenceService {

    private final DocumentVersionRepository documentVersionRepository;
    private final ChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ChunkWriteService chunkWriteService;

    public ChunkPersistenceService(
            DocumentVersionRepository documentVersionRepository,
            ChunkRepository chunkRepository,
            EmbeddingService embeddingService,
            ChunkWriteService chunkWriteService
    ) {
        this.documentVersionRepository = documentVersionRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.chunkWriteService = chunkWriteService;
    }

    public ChunkPersistenceResult persist(
            Long documentId,
            Long versionId,
            MultipartFile file,
            List<TextChunk> chunks
    ) throws IOException {

        validateInput(documentId, versionId, file, chunks);

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

        String source = version.sourceUri() != null
                && !version.sourceUri().isBlank()
                ? version.sourceUri()
                : file.getOriginalFilename();

        List<StoredChunkInfo> existingChunks =
                chunkRepository.findByVersionId(version.versionId());

        if (!existingChunks.isEmpty()) {
            return handleExistingChunks(
                    version,
                    chunks,
                    existingChunks
            );
        }

        List<ChunkEmbedding> embeddings = embeddingService.embed(chunks);

        int insertedCount = chunkWriteService.insertChunksWithEmbeddings(
                version,
                chunks,
                embeddings,
                source
        );

        return new ChunkPersistenceResult(
                version.versionId(),
                insertedCount,
                false
        );
    }

    private ChunkPersistenceResult handleExistingChunks(
            DocumentVersionInfo version,
            List<TextChunk> chunks,
            List<StoredChunkInfo> existingChunks
    ) {
        if (existingChunks.size() != chunks.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Existing chunk count does not match the newly extracted chunk count."
            );
        }

        Map<Integer, TextChunk> chunksByIndex = chunks.stream()
                .collect(Collectors.toMap(
                        TextChunk::chunkIndex,
                        Function.identity()
                ));

        for (StoredChunkInfo stored : existingChunks) {
            TextChunk extracted = chunksByIndex.get(stored.chunkIndex());

            if (extracted == null
                    || !extracted.checksum().equals(stored.checksum())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Existing chunk content does not match the uploaded PDF."
                );
            }
        }

        List<Integer> missingIndices = existingChunks.stream()
                .filter(StoredChunkInfo::embeddingMissing)
                .map(StoredChunkInfo::chunkIndex)
                .toList();

        if (!missingIndices.isEmpty()) {
            List<TextChunk> missingChunks = missingIndices.stream()
                    .map(chunksByIndex::get)
                    .toList();

            List<ChunkEmbedding> embeddings =
                    embeddingService.embed(missingChunks);

            chunkWriteService.updateMissingEmbeddings(
                    version.versionId(),
                    embeddings
            );
        }

        return new ChunkPersistenceResult(
                version.versionId(),
                existingChunks.size(),
                true
        );
    }

    private void validateInput(
            Long documentId,
            Long versionId,
            MultipartFile file,
            List<TextChunk> chunks
    ) {
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