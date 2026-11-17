
package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.dto.ChunkPersistenceResult;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import com.devdocs.ai.ingeststatic.repository.ChunkRepository;
import com.devdocs.ai.ingeststatic.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
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
    private ChunkPersistenceService service;

    private final byte[] pdfBytes =
            "test PDF content".getBytes(StandardCharsets.UTF_8);

    private final List<TextChunk> chunks = List.of(
            new TextChunk(0, 1, "Test chunk content", "chunk-checksum-001")
    );

    @BeforeEach
    void setUp() {
        documentVersionRepository = mock(DocumentVersionRepository.class);
        chunkRepository = mock(ChunkRepository.class);

        service = new ChunkPersistenceService(
                documentVersionRepository,
                chunkRepository
        );
    }

    @Test
    void shouldPersistChunksWhenVersionHasNoExistingChunks() throws Exception {
        DocumentVersionInfo version = createVersion("STATIC");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        when(chunkRepository.countByVersionId(5L))
                .thenReturn(0L, 1L);

        when(chunkRepository.insertAll(version, chunks, "https://example.com/test.pdf"))
                .thenReturn(1);

        ChunkPersistenceResult result = service.persist(
                4L,
                5L,
                createPdfFile(),
                chunks
        );

        assertEquals(5L, result.versionId());
        assertEquals(1, result.chunkCount());
        assertFalse(result.alreadyIngested());

        verify(chunkRepository).insertAll(
                version,
                chunks,
                "https://example.com/test.pdf"
        );
    }

    @Test
    void shouldNotInsertChunksAgainWhenVersionWasAlreadyIngested() throws Exception {
        DocumentVersionInfo version = createVersion("STATIC");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        when(chunkRepository.countByVersionId(5L))
                .thenReturn(1L);

        ChunkPersistenceResult result = service.persist(
                4L,
                5L,
                createPdfFile(),
                chunks
        );

        assertEquals(5L, result.versionId());
        assertEquals(1, result.chunkCount());
        assertTrue(result.alreadyIngested());

        verify(chunkRepository, never()).insertAll(
                any(),
                anyList(),
                anyString()
        );
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

        verify(chunkRepository, never()).insertAll(
                any(),
                anyList(),
                anyString()
        );
    }

    @Test
    void shouldRejectWhenDocumentVersionDoesNotExist() {
        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                service.persist(4L, 5L, createPdfFile(), chunks)
        );

        verify(chunkRepository, never()).insertAll(
                any(),
                anyList(),
                anyString()
        );
    }

    @Test
    void shouldRejectNonStaticDocument() throws Exception {
        DocumentVersionInfo version = createVersion("WEB");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        assertThrows(ResponseStatusException.class, () ->
                service.persist(4L, 5L, createPdfFile(), chunks)
        );

        verify(chunkRepository, never()).insertAll(
                any(),
                anyList(),
                anyString()
        );
    }

    @Test
    void shouldRejectWhenExistingChunkCountDoesNotMatch() throws Exception {
        DocumentVersionInfo version = createVersion("STATIC");

        when(documentVersionRepository.findByVersionIdAndDocumentId(5L, 4L))
                .thenReturn(Optional.of(version));

        when(chunkRepository.countByVersionId(5L))
                .thenReturn(2L);

        assertThrows(ResponseStatusException.class, () ->
                service.persist(4L, 5L, createPdfFile(), chunks)
        );

        verify(chunkRepository, never()).insertAll(
                any(),
                anyList(),
                anyString()
        );
    }

    private DocumentVersionInfo createVersion(String sourceType) throws Exception {
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
