
package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.chunk.TextChunker;
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

    private final PdfExtractionService service =
            new PdfExtractionService(
                    new TextChunker(1000, 200),
                    new PdfLayoutTextExtractor()
            );

    @Test
    void shouldExtractTextAndCreateChunksFromValidPdf() throws Exception {
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

        assertFalse(response.chunks().isEmpty());

        TextChunk chunk = response.chunks().get(0);

        assertEquals(0, chunk.chunkIndex());
        assertEquals(1, chunk.pageNumber());
        assertTrue(chunk.content().contains("DevDocs AI PDF extraction test"));
        assertEquals(64, chunk.checksum().length());
    }

    @Test
    void shouldExtractTextAndCreateChunksForMultiplePages() throws Exception {
        byte[] pdfBytes = createTwoPageTestPdf();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "two-page-document.pdf",
                "application/pdf",
                pdfBytes
        );

        PdfExtractionResponse response = service.extract(file);

        assertEquals(2, response.pageCount());
        assertEquals(2, response.pages().size());

        assertEquals(1, response.pages().get(0).pageNumber());
        assertEquals(2, response.pages().get(1).pageNumber());

        assertTrue(response.pages().get(0).text().contains("First page content"));
        assertTrue(response.pages().get(1).text().contains("Second page content"));

        assertTrue(response.extractedText().contains("First page content"));
        assertTrue(response.extractedText().contains("Second page content"));

        assertTrue(response.chunks().stream()
                .anyMatch(chunk -> chunk.pageNumber() == 1
                        && chunk.content().contains("First page content")));

        assertTrue(response.chunks().stream()
                .anyMatch(chunk -> chunk.pageNumber() == 2
                        && chunk.content().contains("Second page content")));
    }

    @Test
    void shouldKeepTableCellsTogetherInVisualRowOrder() throws Exception {
        byte[] pdfBytes = createPositionedPdf(new String[][]{
                {"Task", "Owner", "Cadence"},
                {"Review", "Security Team", "Quarterly"},
                {"Approve", "Executives", "Annual"},
                {"Publish", "IT Operations", "As needed"}
        }, new float[]{50, 220, 390});

        PdfExtractionResponse response = service.extract(new MockMultipartFile(
                "file", "table.pdf", "application/pdf", pdfBytes));

        String text = response.pages().getFirst().text();
        assertTrue(text.contains("Task | Owner | Cadence"), text);
        assertTrue(text.contains("Review | Security Team | Quarterly"), text);
        assertTrue(text.indexOf("Review") < text.indexOf("Approve"), text);
    }

    @Test
    void shouldReadSeparateTextColumnsDownTheLeftThenRightColumn() throws Exception {
        byte[] pdfBytes = createPositionedPdf(new String[][]{
                {"Left first line", "Right first line"},
                {"Left second line", "Right second line"},
                {"Left third line", "Right third line"},
                {"Left fourth line", "Right fourth line"}
        }, new float[]{50, 330});

        PdfExtractionResponse response = service.extract(new MockMultipartFile(
                "file", "two-column.pdf", "application/pdf", pdfBytes));

        String text = response.pages().getFirst().text();
        assertTrue(text.indexOf("Left first line") < text.indexOf("Left fourth line"), text);
        assertTrue(text.indexOf("Left fourth line") < text.indexOf("Right first line"), text);
        assertTrue(text.indexOf("Right first line") < text.indexOf("Right fourth line"), text);
    }

    @Test
    void shouldJoinLineEndHyphenationAndRemoveRepeatedRunningHeaders() throws Exception {
        byte[] pdfBytes = createTwoPageWrappedPdf();
        PdfExtractionResponse response = service.extract(new MockMultipartFile(
                "file", "wrapped.pdf", "application/pdf", pdfBytes));

        assertTrue(response.pages().getFirst().text().contains("security"));
        assertFalse(response.pages().getFirst().text().contains("secu-\nrity"));
        assertFalse(response.pages().get(1).text().contains("Repeated handbook header"));
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

    private byte[] createTwoPageTestPdf() throws Exception {
        try (
                PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            String[] pageTexts = {
                    "First page content",
                    "Second page content"
            };

            for (String pageText : pageTexts) {
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
                    contentStream.showText(pageText);
                    contentStream.endText();
                }
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPositionedPdf(String[][] rows, float[] columns)
            throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            for (int row = 0; row < rows.length; row++) {
                for (int column = 0; column < rows[row].length; column++) {
                    try (PDPageContentStream stream = new PDPageContentStream(
                            document, page, PDPageContentStream.AppendMode.APPEND,
                            true, true)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        stream.newLineAtOffset(columns[column], 700 - row * 24);
                        stream.showText(rows[row][column]);
                        stream.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createTwoPageWrappedPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String[] lines : new String[][]{
                    {"Repeated handbook header", "The secu-", "rity baseline is active."},
                    {"Repeated handbook header", "Page two body text."}
            }) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    for (int i = 0; i < lines.length; i++) {
                        if (i > 0) stream.newLineAtOffset(0, -20);
                        stream.showText(lines[i]);
                    }
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
