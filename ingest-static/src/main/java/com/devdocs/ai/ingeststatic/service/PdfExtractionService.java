package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.dto.PdfExtractionResponse;
import com.devdocs.ai.ingeststatic.exception.InvalidPdfException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
public class PdfExtractionService {

    public PdfExtractionResponse extract(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new InvalidPdfException("PDF file must not be empty.");
        }

        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null
                || !originalFileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new InvalidPdfException("Only PDF files are allowed.");
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            PDFTextStripper textStripper = new PDFTextStripper();
            String extractedText = textStripper.getText(document);

            return new PdfExtractionResponse(
                    originalFileName,
                    document.getNumberOfPages(),
                    extractedText.length(),
                    extractedText
            );

        } catch (IOException exception) {
            throw new InvalidPdfException(
                    "The uploaded file is not a valid or readable PDF."
            );
        }
    }
}
