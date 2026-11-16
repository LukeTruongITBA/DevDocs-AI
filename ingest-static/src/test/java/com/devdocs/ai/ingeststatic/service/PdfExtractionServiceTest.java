package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.dto.PdfExtractionResponse;
import com.devdocs.ai.ingeststatic.exception.InvalidPdfException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PdfExtractionServiceTest {

    private final PdfExtractionService service = new PdfExtractionService();

    @Test
    void shouldExtractTextFromValidPdf() throws Exception {
        byte[] pdfBytes = createTestPdf();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                pdfBytes
        );

        PdfExtractionResponse response = service.extract(file);

        assertEquals("test-document.pdf", response.fileName());
        assertEquals(1, response.pageCount());
        assertTrue(response.characterCount() > 0);
        assertTrue(response.extractedText().contains("DevDocs AI PDF extraction test"));
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThrows(InvalidPdfException.class, () -> service.extract(file));
    }

    @Test
    void shouldRejectNonPdfFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "This is not a PDF".getBytes()
        );

        assertThrows(InvalidPdfException.class, () -> service.extract(file));
    }

    @Test
    void shouldRejectCorruptedPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "corrupted.pdf",
                "application/pdf",
                "This is not a valid PDF".getBytes()
        );

        assertThrows(InvalidPdfException.class, () -> service.extract(file));
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
