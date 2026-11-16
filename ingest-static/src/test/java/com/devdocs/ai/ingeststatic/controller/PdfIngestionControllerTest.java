package com.devdocs.ai.ingeststatic.controller;

import com.devdocs.ai.ingeststatic.exception.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PdfIngestionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PdfExtractionService service = new PdfExtractionService();

        PdfIngestionController controller =
                new PdfIngestionController(service);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnExtractedPdfAsJson() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                createTestPdf()
        );

        mockMvc.perform(multipart("/api/ingestion/static/pdf").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("test-document.pdf"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.characterCount").isNumber())
                .andExpect(jsonPath("$.extractedText")
                        .value(org.hamcrest.Matchers.containsString(
                                "DevDocs AI PDF extraction test"
                        )));
    }

    @Test
    void shouldReturn400ForEmptyPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/ingestion/static/pdf").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid PDF"));
    }

    @Test
    void shouldReturn400ForNonPdfFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "Not a PDF".getBytes()
        );

        mockMvc.perform(multipart("/api/ingestion/static/pdf").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid PDF"));
    }

    private byte[] createTestPdf() throws Exception {
        try (
                PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
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

            document.save(output);
            return output.toByteArray();
        }
    }
}
