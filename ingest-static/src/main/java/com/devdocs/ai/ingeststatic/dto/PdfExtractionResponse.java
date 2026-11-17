
package com.devdocs.ai.ingeststatic.dto;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;

import java.util.List;

public record PdfExtractionResponse(
        String fileName,
        int pageCount,
        int characterCount,
        String extractedText,
        List<PdfPageText> pages,
        List<TextChunk> chunks,
        ChunkPersistenceResult persistence
) {

    public PdfExtractionResponse(
            String fileName,
            int pageCount,
            int characterCount,
            String extractedText,
            List<PdfPageText> pages,
            List<TextChunk> chunks
    ) {
        this(
                fileName,
                pageCount,
                characterCount,
                extractedText,
                pages,
                chunks,
                null
        );
    }
}
