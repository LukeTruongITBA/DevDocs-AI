
package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.chunk.TextChunker;
import com.devdocs.ai.ingeststatic.dto.PdfExtractionResponse;
import com.devdocs.ai.ingeststatic.dto.PdfPageText;
import com.devdocs.ai.ingeststatic.exception.InvalidPdfException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PdfExtractionService {

    private final TextChunker textChunker;

    public PdfExtractionService(TextChunker textChunker) {
        this.textChunker = textChunker;
    }

    public PdfExtractionResponse extract(MultipartFile file)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new InvalidPdfException(
                    "PDF file must not be empty."
            );
        }

        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null
                || !originalFileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new InvalidPdfException(
                    "Only PDF files are allowed."
            );
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            int pageCount = document.getNumberOfPages();

            List<PdfPageText> pages = new ArrayList<>();
            StringBuilder extractedText = new StringBuilder();

            for (int pageNumber = 1;
                 pageNumber <= pageCount;
                 pageNumber++) {

                PDFTextStripper textStripper = new PDFTextStripper();

                textStripper.setStartPage(pageNumber);
                textStripper.setEndPage(pageNumber);

                String pageText = textStripper.getText(document);

                pages.add(new PdfPageText(
                        pageNumber,
                        pageText
                ));

                if (pageNumber > 1) {
                    extractedText.append("\n");
                }

                extractedText.append(pageText);
            }

            String fullText = extractedText.toString();

            List<TextChunk> chunks = textChunker.chunkPages(pages);

            return new PdfExtractionResponse(
                    originalFileName,
                    pageCount,
                    fullText.length(),
                    fullText,
                    List.copyOf(pages),
                    chunks
            );

        } catch (IOException exception) {
            throw new InvalidPdfException(
                    "The uploaded file is not a valid or readable PDF."
            );
        }
    }
}
