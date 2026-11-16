package com.devdocs.ai.ingeststatic.dto;

public record PdfExtractionResponse(
        String fileName,
        int pageCount,
        int characterCount,
        String extractedText
) {
}
