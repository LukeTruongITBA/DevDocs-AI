
package com.devdocs.ai.ingeststatic.controller;

import com.devdocs.ai.ingeststatic.chunk.TextChunker;
import com.devdocs.ai.ingeststatic.dto.ChunkPersistenceResult;
import com.devdocs.ai.ingeststatic.exception.GlobalExceptionHandler;
import com.devdocs.ai.ingeststatic.service.ChunkPersistenceService;
import com.devdocs.ai.ingeststatic.service.PdfExtractionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PdfIngestionControllerTest {

    private MockMvc mockMvc;
    private ChunkPersistenceService chunkPersistenceService;

    @BeforeEach
    void setUp() {
        PdfExtractionService pdfExtractionService =
                new PdfExtractionService(new TextChunker(1000, 200));

        chunkPersistenceService = mock(ChunkPersistenceService.class);

        PdfIngestionController controller = new PdfIngestionController(
                pdfExtractionService,
                chunkPersistenceService
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExtractAndPersistPdfChunks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                createTestPdf()
        );

        when(chunkPersistenceService.persist(
                eq(4L),
                eq(5L),
                any(MultipartFile.class),
                anyList()
        )).thenReturn(new ChunkPersistenceResult(5L, 1, false));

        mockMvc.perform(multipart("/api/ingestion/static/pdf")
                        .file(file)
                        .param("documentId", "4")
                        .param("versionId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("test-document.pdf"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.characterCount").isNumber())
                .andExpect(jsonPath("$.extractedText")
                        .value(org.hamcrest.Matchers.containsString(
                                "DevDocs AI PDF extraction test"
                        )))
                .andExpect(jsonPath("$.chunks").isArray())
                .andExpect(jsonPath("$.chunks.length()").value(1))
                .andExpect(jsonPath("$.persistence.versionId").value(5))
                .andExpect(jsonPath("$.persistence.chunkCount").value(1))
                .andExpect(jsonPath("$.persistence.alreadyIngested").value(false));

        verify(chunkPersistenceService).persist(
                eq(4L),
                eq(5L),
                any(MultipartFile.class),
                anyList()
        );
    }

    @Test
    void shouldReturn400ForEmptyPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/ingestion/static/pdf")
                        .file(file)
                        .param("documentId", "4")
                        .param("versionId", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400ForNonPdfFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "This is not a PDF".getBytes()
        );

        mockMvc.perform(multipart("/api/ingestion/static/pdf")
                        .file(file)
                        .param("documentId", "4")
                        .param("versionId", "5"))
                .andExpect(status().isBadRequest());
    }

    private byte[] createTestPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream =
                         new PDPageContentStream(document, page)) {

                contentStream.beginText();
                contentStream.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12
                );
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("DevDocs AI PDF extraction test");
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
