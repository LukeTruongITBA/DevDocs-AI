package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.ChunkEmbedding;
import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.dto.ChunkPersistenceResult;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import com.devdocs.ai.ingeststatic.repository.ChunkRepository;
import com.devdocs.ai.ingeststatic.repository.DocumentVersionRepository;
import com.devdocs.ai.ingeststatic.repository.StoredChunkInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkPersistenceServiceTest {

    private DocumentVersionRepository documentVersionRepository;
    private ChunkRepository chunkRepository;
    private EmbeddingService embeddingService;
    private ChunkWriteService chunkWriteService;
    private ChunkPersistenceService service;

    private final byte[] pdfBytes =
            "test PDF content".getBytes(StandardCharsets.UTF_8);

    private final List<TextChunk> chunks = List.of(
            new TextChunk(0, 1, "Test chunk content", "chunk-checksum-001")
    );

    private final List<ChunkEmbedding> embeddings = List.of(
            new ChunkEmbedding(0, List.of(0.1, 0.2, 0.3))
    );

    @BeforeEach
    void setUp() {
        documentVersionRepository = mock(DocumentVersionRepository.class);
        chunkRepository = mock(ChunkRepository.class);
        embeddingService = mock(EmbeddingService.class);
        chunkWriteService = mock(ChunkWriteService.class);

        service = new ChunkPersistenceService(
                documentVersionRepository,
                chunkRepository,
                embeddingService,
                chunkWriteService
        );
    }

    @Test
    void shouldGenerateAndPersistEmbeddingsForNewChunks() throws Exception {
        DocumentVersionInfo version = createVersion("STATIC");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        when(chunkRepository.findByVersionId(5L))
                .thenReturn(List.of());

        when(embeddingService.embed(chunks))
                .thenReturn(embeddings);

        when(chunkWriteService.insertChunksWithEmbeddings(
                version,
                chunks,
                embeddings,
                "https://example.com/test.pdf"
        )).thenReturn(1);

        ChunkPersistenceResult result = service.persist(
                4L,
                5L,
                createPdfFile(),
                chunks
        );

        assertEquals(5L, result.versionId());
        assertEquals(1, result.chunkCount());
        assertFalse(result.alreadyIngested());

        verify(embeddingService).embed(chunks);

        verify(chunkWriteService).insertChunksWithEmbeddings(
                version,
                chunks,
                embeddings,
                "https://example.com/test.pdf"
        );
    }

    @Test
    void shouldNotGenerateEmbeddingsAgainWhenChunksAlreadyExist()
            throws Exception {

        DocumentVersionInfo version = createVersion("STATIC");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        when(chunkRepository.findByVersionId(5L))
                .thenReturn(List.of(
                        new StoredChunkInfo(
                                0,
                                "chunk-checksum-001",
                                false
                        )
                ));

        ChunkPersistenceResult result = service.persist(
                4L,
                5L,
                createPdfFile(),
                chunks
        );

        assertEquals(5L, result.versionId());
        assertEquals(1, result.chunkCount());
        assertTrue(result.alreadyIngested());

        verify(embeddingService, never()).embed(anyList());

        verify(chunkWriteService, never()).insertChunksWithEmbeddings(
                any(),
                anyList(),
                anyList(),
                anyString()
        );

        verify(chunkWriteService, never()).updateMissingEmbeddings(
                anyLong(),
                anyList()
        );
    }

    @Test
    void shouldGenerateAndUpdateMissingEmbeddings() throws Exception {
        DocumentVersionInfo version = createVersion("STATIC");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        when(chunkRepository.findByVersionId(5L))
                .thenReturn(List.of(
                        new StoredChunkInfo(
                                0,
                                "chunk-checksum-001",
                                true
                        )
                ));

        when(embeddingService.embed(chunks))
                .thenReturn(embeddings);

        ChunkPersistenceResult result = service.persist(
                4L,
                5L,
                createPdfFile(),
                chunks
        );

        assertEquals(5L, result.versionId());
        assertEquals(1, result.chunkCount());
        assertTrue(result.alreadyIngested());

        verify(embeddingService).embed(chunks);

        verify(chunkWriteService).updateMissingEmbeddings(
                5L,
                embeddings
        );

        verify(chunkWriteService, never()).insertChunksWithEmbeddings(
                any(),
                anyList(),
                anyList(),
                anyString()
        );
    }

    @Test
    void shouldCompletePartiallyPersistedVersion() throws Exception {
        DocumentVersionInfo version = createVersion("STATIC");
        TextChunk second = new TextChunk(1, 1, "Second chunk", "d".repeat(64));
        List<TextChunk> allChunks = List.of(chunks.getFirst(), second);
        ChunkEmbedding secondEmbedding = new ChunkEmbedding(1, List.of(0.4, 0.5, 0.6));

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));
        when(chunkRepository.findByVersionId(5L))
                .thenReturn(List.of(new StoredChunkInfo(0, "chunk-checksum-001", false)));
        when(embeddingService.embed(List.of(second))).thenReturn(List.of(secondEmbedding));
        when(chunkWriteService.insertChunksWithEmbeddings(
                version, List.of(second), List.of(secondEmbedding),
                "https://example.com/test.pdf", 2
        )).thenReturn(1);

        ChunkPersistenceResult result = service.persist(
                4L, 5L, createPdfFile(), allChunks
        );

        assertEquals(2, result.chunkCount());
        assertTrue(result.alreadyIngested());
        verify(chunkWriteService).insertChunksWithEmbeddings(
                version, List.of(second), List.of(secondEmbedding),
                "https://example.com/test.pdf", 2
        );
    }

    @Test
    void shouldRecoverConcurrentDuplicateInsertAsIdempotentSuccess() throws Exception {
        DocumentVersionInfo version = createVersion("STATIC");
        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));
        when(chunkRepository.findByVersionId(5L))
                .thenReturn(List.of())
                .thenReturn(List.of(new StoredChunkInfo(0, "chunk-checksum-001", false)));
        when(embeddingService.embed(chunks)).thenReturn(embeddings);
        when(chunkWriteService.insertChunksWithEmbeddings(
                version, chunks, embeddings, "https://example.com/test.pdf"
        )).thenThrow(new DuplicateKeyException("concurrent insert"));

        ChunkPersistenceResult result = service.persist(
                4L, 5L, createPdfFile(), chunks
        );

        assertTrue(result.alreadyIngested());
        assertEquals(1, result.chunkCount());
    }

    @Test
    void shouldRejectPdfWhenChecksumDoesNotMatchVersion() {
        DocumentVersionInfo version = new DocumentVersionInfo(
                5L,
                4L,
                "1.0",
                "incorrect-checksum",
                "https://example.com/test.pdf",
                "STATIC"
        );

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        assertThrows(ResponseStatusException.class, () ->
                service.persist(4L, 5L, createPdfFile(), chunks)
        );

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(chunkWriteService);
    }

    @Test
    void shouldRejectWhenDocumentVersionDoesNotExist() {
        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                service.persist(4L, 5L, createPdfFile(), chunks)
        );

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(chunkWriteService);
    }

    @Test
    void shouldRejectNonStaticDocument() throws Exception {
        DocumentVersionInfo version = createVersion("WEB");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        assertThrows(ResponseStatusException.class, () ->
                service.persist(4L, 5L, createPdfFile(), chunks)
        );

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(chunkWriteService);
    }

    @Test
    void shouldRejectWhenExistingChunkCountDoesNotMatch()
            throws Exception {

        DocumentVersionInfo version = createVersion("STATIC");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        when(chunkRepository.findByVersionId(5L))
                .thenReturn(List.of(
                        new StoredChunkInfo(0, "checksum-0", false),
                        new StoredChunkInfo(1, "checksum-1", false)
                ));

        assertThrows(ResponseStatusException.class, () ->
                service.persist(4L, 5L, createPdfFile(), chunks)
        );

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(chunkWriteService);
    }

    @Test
    void shouldRejectWhenExistingChunkChecksumDoesNotMatch()
            throws Exception {

        DocumentVersionInfo version = createVersion("STATIC");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        when(chunkRepository.findByVersionId(5L))
                .thenReturn(List.of(
                        new StoredChunkInfo(
                                0,
                                "different-checksum",
                                false
                        )
                ));

        assertThrows(ResponseStatusException.class, () ->
                service.persist(4L, 5L, createPdfFile(), chunks)
        );

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(chunkWriteService);
    }

    private DocumentVersionInfo createVersion(String sourceType)
            throws Exception {

        return new DocumentVersionInfo(
                5L,
                4L,
                "1.0",
                calculateSha256(pdfBytes),
                "https://example.com/test.pdf",
                sourceType
        );
    }

    private MockMultipartFile createPdfFile() {
        return new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                pdfBytes
        );
    }

    private String calculateSha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }
}
