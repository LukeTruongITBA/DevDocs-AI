package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.ChunkEmbedding;
import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import com.devdocs.ai.ingeststatic.repository.ChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChunkWriteService {

    private final ChunkRepository chunkRepository;

    public ChunkWriteService(ChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public int insertChunksWithEmbeddings(
            DocumentVersionInfo version,
            List<TextChunk> chunks,
            List<ChunkEmbedding> embeddings,
            String source
    ) {
        return insertChunksWithEmbeddings(
                version, chunks, embeddings, source, chunks.size()
        );
    }

    @Transactional
    public int insertChunksWithEmbeddings(
            DocumentVersionInfo version,
            List<TextChunk> chunks,
            List<ChunkEmbedding> embeddings,
            String source,
            int expectedVersionChunkCount
    ) {
        int insertedCount = chunkRepository.insertAllWithEmbeddings(
                version,
                chunks,
                embeddings,
                source
        );

        long storedCount = chunkRepository.countByVersionId(
                version.versionId()
        );

        if (insertedCount != chunks.size()
                || storedCount != expectedVersionChunkCount) {
            throw new IllegalStateException(
                    "Chunk and embedding persistence verification failed. "
                            + "Expected " + expectedVersionChunkCount
                            + " chunks but found " + storedCount + "."
            );
        }

        return insertedCount;
    }

    @Transactional
    public int updateMissingEmbeddings(
            Long versionId,
            List<ChunkEmbedding> embeddings
    ) {
        int updatedCount = chunkRepository.updateMissingEmbeddings(
                versionId,
                embeddings
        );

        boolean missingEmbeddingsRemain = chunkRepository
                .findByVersionId(versionId)
                .stream()
                .anyMatch(chunkInfo ->
                        chunkInfo.embeddingMissing()
                );

        if (missingEmbeddingsRemain) {
            throw new IllegalStateException(
                    "Some chunks still have missing embeddings."
            );
        }

        return updatedCount;
    }
}
