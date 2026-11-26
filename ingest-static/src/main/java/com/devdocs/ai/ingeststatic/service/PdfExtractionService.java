
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);
    private static final Pattern WRAPPED_HYPHEN = Pattern.compile(
            "(?<=[\\p{L}])-\\h*\\R\\h*(?=[\\p{Ll}])"
    );

    private final TextChunker textChunker;
    private final PdfLayoutTextExtractor layoutTextExtractor;

    public PdfExtractionService(
            TextChunker textChunker,
            PdfLayoutTextExtractor layoutTextExtractor
    ) {
        this.textChunker = textChunker;
        this.layoutTextExtractor = layoutTextExtractor;
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

            List<String> pageTexts = new ArrayList<>();
            StringBuilder extractedText = new StringBuilder();

            for (int pageNumber = 1;
                 pageNumber <= pageCount;
                 pageNumber++) {

                pageTexts.add(layoutTextExtractor.extractPage(document, pageNumber));
            }

            removeRepeatedRunningText(pageTexts);
            List<PdfPageText> pages = new ArrayList<>();
            for (int i = 0; i < pageTexts.size(); i++) {
                String pageText = normalizePageText(pageTexts.get(i));
                pages.add(new PdfPageText(i + 1, pageText));
                if (i > 0) extractedText.append("\n\n");
                extractedText.append(pageText);
            }

            String fullText = extractedText.toString();

            List<TextChunk> chunks = textChunker.chunkPages(pages);

            log.info("PDF extraction completed: pages={}, characters={}, chunks={}",
                    pageCount, fullText.length(), chunks.size());

            return new PdfExtractionResponse(
                    originalFileName,
                    pageCount,
                    fullText.length(),
                    fullText,
                    List.copyOf(pages),
                    chunks
            );

        } catch (IOException exception) {
            log.warn("PDF extraction failed because the uploaded document could not be read.");
            throw new InvalidPdfException(
                    "The uploaded file is not a valid or readable PDF."
            );
        }
    }

    private void removeRepeatedRunningText(List<String> pages) {
        if (pages.size() < 2) return;
        Map<String, Integer> firstLineCounts = new HashMap<>();
        for (String page : pages) {
            String first = firstNonBlankLine(page);
            if (!first.isBlank()) firstLineCounts.merge(first, 1, Integer::sum);
        }
        String repeatedHeader = firstLineCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1
                        && entry.getValue() * 2 >= pages.size())
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

        if (repeatedHeader.isBlank()) return;
        for (int i = 0; i < pages.size(); i++) {
            String[] lines = pages.get(i).split("\\R", -1);
            boolean removed = false;
            StringBuilder cleaned = new StringBuilder();
            for (String line : lines) {
                if (!removed && line.trim().equals(repeatedHeader)) {
                    removed = true;
                    continue;
                }
                if (cleaned.length() > 0) cleaned.append('\n');
                cleaned.append(line);
            }
            pages.set(i, cleaned.toString());
        }
    }

    private String firstNonBlankLine(String text) {
        return text.lines().map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst().orElse("");
    }

    private String normalizePageText(String text) {
        String joined = WRAPPED_HYPHEN.matcher(text).replaceAll("");
        String[] lines = joined.replace("\r\n", "\n").split("\n", -1);
        int lastContentLine = lines.length - 1;
        while (lastContentLine >= 0 && lines[lastContentLine].isBlank()) {
            lastContentLine--;
        }
        if (lastContentLine >= 0 && lines[lastContentLine].trim().matches("\\d+")) {
            lines[lastContentLine] = "";
        }
        return String.join("\n", lines).replaceAll("[\\t ]+\\n", "\n").trim();
    }
}
