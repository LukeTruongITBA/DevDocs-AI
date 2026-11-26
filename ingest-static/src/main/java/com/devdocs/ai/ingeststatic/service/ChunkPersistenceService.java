package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.ChunkEmbedding;
import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.dto.ChunkPersistenceResult;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import com.devdocs.ai.ingeststatic.repository.ChunkRepository;
import com.devdocs.ai.ingeststatic.repository.DocumentVersionRepository;
import com.devdocs.ai.ingeststatic.repository.StoredChunkInfo;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ChunkPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChunkPersistenceService.class);

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
            log.warn("Rejected static ingestion because the source checksum did not match a registered version.");
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
                    existingChunks,
                    source
            );
        }

        List<ChunkEmbedding> embeddings = embeddingService.embed(chunks);

        int insertedCount;
        try {
            insertedCount = chunkWriteService.insertChunksWithEmbeddings(
                    version, chunks, embeddings, source
            );
        } catch (DuplicateKeyException race) {
            List<StoredChunkInfo> concurrentChunks =
                    chunkRepository.findByVersionId(version.versionId());
            if (concurrentChunks.isEmpty()) throw race;
            log.info("Concurrent duplicate ingestion resolved for versionId={}", version.versionId());
            return handleExistingChunks(version, chunks, concurrentChunks, source);
        }

        log.info("Persisted static PDF chunks: versionId={}, chunkCount={}",
                version.versionId(), insertedCount);

        return new ChunkPersistenceResult(
                version.versionId(),
                insertedCount,
                false
        );
    }

    private ChunkPersistenceResult handleExistingChunks(
            DocumentVersionInfo version,
            List<TextChunk> chunks,
            List<StoredChunkInfo> existingChunks,
            String source
    ) {
        Map<Integer, TextChunk> chunksByIndex = chunks.stream()
                .collect(Collectors.toMap(
                        TextChunk::chunkIndex,
                        Function.identity()
                ));

        Set<Integer> storedIndices = new HashSet<>();
        Set<Integer> missingEmbeddingIndices = new HashSet<>();
        for (StoredChunkInfo stored : existingChunks) {
            TextChunk extracted = chunksByIndex.get(stored.chunkIndex());

            if (extracted == null
                    || !extracted.checksum().equals(stored.checksum())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Existing chunk content does not match the uploaded PDF."
                );
            }
            storedIndices.add(stored.chunkIndex());
            if (stored.embeddingMissing()) {
                missingEmbeddingIndices.add(stored.chunkIndex());
            }
        }

        List<TextChunk> newChunks = chunks.stream()
                .filter(chunk -> !storedIndices.contains(chunk.chunkIndex()))
                .toList();
        Map<Integer, TextChunk> chunksToEmbed = new java.util.LinkedHashMap<>();
        for (Integer index : missingEmbeddingIndices) {
            chunksToEmbed.put(index, chunksByIndex.get(index));
        }
        for (TextChunk chunk : newChunks) {
            chunksToEmbed.put(chunk.chunkIndex(), chunk);
        }

        if (!chunksToEmbed.isEmpty()) {
            List<ChunkEmbedding> embeddings = embeddingService.embed(
                    List.copyOf(chunksToEmbed.values())
            );
            List<ChunkEmbedding> embeddingsForExisting = embeddings.stream()
                    .filter(embedding -> missingEmbeddingIndices.contains(embedding.chunkIndex()))
                    .toList();
            if (!embeddingsForExisting.isEmpty()) {
                chunkWriteService.updateMissingEmbeddings(
                        version.versionId(), embeddingsForExisting
                );
            }
            List<ChunkEmbedding> embeddingsForNew = embeddings.stream()
                    .filter(embedding -> newChunks.stream().anyMatch(chunk ->
                            chunk.chunkIndex() == embedding.chunkIndex()))
                    .toList();
            if (!newChunks.isEmpty()) {
                try {
                    chunkWriteService.insertChunksWithEmbeddings(
                            version, newChunks, embeddingsForNew, source, chunks.size()
                    );
                } catch (DuplicateKeyException race) {
                    List<StoredChunkInfo> refreshed =
                            chunkRepository.findByVersionId(version.versionId());
                    if (refreshed.size() <= existingChunks.size()) throw race;
                    return handleExistingChunks(version, chunks, refreshed, source);
                }
            }
        }

        if (existingChunks.size() == chunks.size() && chunksToEmbed.isEmpty()) {
            log.info("Static PDF ingestion is already complete: versionId={}, chunkCount={}",
                    version.versionId(), existingChunks.size());
        } else {
            log.info("Completed partial static PDF ingestion: versionId={}, existingChunks={}, totalChunks={}",
                    version.versionId(), existingChunks.size(), chunks.size());
        }

        return new ChunkPersistenceResult(
                version.versionId(),
                chunks.size(),
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
