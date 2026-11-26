
package com.devdocs.ai.ingeststatic.service;

import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.chunk.TextChunker;
import com.devdocs.ai.ingeststatic.dto.PdfExtractionResponse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PdfSampleExtractionReportTest {

    private record Segment(float x, float y, String text) {}

    private static class PageFourGraphicsInspector
            extends PDFGraphicsStreamEngine {

        private final Path2D.Float currentPath = new Path2D.Float();
        private int pathCount;

        protected PageFourGraphicsInspector(PDPage page) {
            super(page);
        }

        /*
         * PDFBox 3.0.8 already applies the current transformation matrix
         * before calling moveTo(), lineTo(), and curveTo().
         *
         * Therefore, coordinates received by these callbacks must NOT
         * be transformed again.
         */

        @Override
        public void moveTo(float x, float y) {
            currentPath.moveTo(x, y);
        }

        @Override
        public void lineTo(float x, float y) {
            currentPath.lineTo(x, y);
        }

        @Override
        public void curveTo(
                float x1, float y1,
                float x2, float y2,
                float x3, float y3
        ) {
            currentPath.curveTo(
                    x1, y1,
                    x2, y2,
                    x3, y3
            );
        }

        @Override
        public Point2D getCurrentPoint() {
            return currentPath.getCurrentPoint();
        }

        @Override
        public void closePath() {
            currentPath.closePath();
        }

        @Override
        public void appendRectangle(
                Point2D p0,
                Point2D p1,
                Point2D p2,
                Point2D p3
        ) {
            /*
             * PDFBox has already transformed these four points
             * before invoking appendRectangle().
             * Do not apply another transformation here.
             */
            currentPath.moveTo(p0.getX(), p0.getY());
            currentPath.lineTo(p1.getX(), p1.getY());
            currentPath.lineTo(p2.getX(), p2.getY());
            currentPath.lineTo(p3.getX(), p3.getY());
            currentPath.closePath();
        }

        @Override
        public void drawImage(PDImage image) {
            // Images are not needed for this vector-path diagnostic.
        }

        @Override
        public void clip(int windingRule) {
            // Clipping is not needed for this diagnostic.
        }

        @Override
        public void endPath() {
            currentPath.reset();
        }

        @Override
        public void strokePath() {
            reportPath("STROKE");
        }

        @Override
        public void fillPath(int windingRule) {
            reportPath("FILL");
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            reportPath("FILL_STROKE");
        }

        @Override
        public void shadingFill(
                org.apache.pdfbox.cos.COSName shadingName
        ) {
            // Shading is not needed for this diagnostic.
        }

        private void reportPath(String operation) {
            if (currentPath.getCurrentPoint() == null) {
                currentPath.reset();
                return;
            }

            Rectangle2D bounds = currentPath.getBounds2D();

            System.out.printf(
                    Locale.ROOT,
                    "%03d | %-11s | X=[%.2f, %.2f] Y=[%.2f, %.2f] | W=%.2f H=%.2f%n",
                    ++pathCount,
                    operation,
                    bounds.getMinX(),
                    bounds.getMaxX(),
                    bounds.getMinY(),
                    bounds.getMaxY(),
                    bounds.getWidth(),
                    bounds.getHeight()
            );

            currentPath.reset();
        }

        int getPathCount() {
            return pathCount;
        }
    }

    private final PdfExtractionService service =
            new PdfExtractionService(
                    new TextChunker(1000, 200),
                    new PdfLayoutTextExtractor()
            );

    private final Path sampleDirectory = Path.of(
            System.getProperty("user.home"),
            "Desktop",
            "UnitBV",
            "Anul1_Se1",
            "[IAPC]",
            "Document",
            "Lab3",
            "qa-over-internal-docs"
    );

    private static final List<String> EXPECTED_FILES = List.of(
            "authentication_datahandling.pdf",
            "compliance_responsibilities.pdf",
            "policy_incidentmanagement.pdf",
            "scope_roles.pdf",
            "secure_development.pdf",
            "updatecycle_checklist.pdf"
    );

    @BeforeEach
    void requireExternalSampleSet() {
        boolean sampleSetAvailable = Files.isDirectory(sampleDirectory)
                && EXPECTED_FILES.stream().allMatch(fileName ->
                        Files.isRegularFile(sampleDirectory.resolve(fileName))
                );
        Assumptions.assumeTrue(
                sampleSetAvailable,
                "External professor PDF sample set is unavailable; "
                        + "sample diagnostics require all six files at "
                        + sampleDirectory
        );
    }

    @Test
    void shouldExtractAllSixProfessorSamplePdfsAndPrintReport()
            throws Exception {

        assertTrue(
                Files.isDirectory(sampleDirectory),
                "Professor sample directory does not exist: " + sampleDirectory
        );

        System.out.println(
                "\n========== PROFESSOR PDF SAMPLE EXTRACTION REPORT =========="
        );
        System.out.println("Directory: " + sampleDirectory);
        System.out.println("Chunk size: 1000");
        System.out.println("Chunk overlap: 200");

        int totalPages = 0;
        int totalCharacters = 0;
        int totalChunks = 0;

        for (String fileName : EXPECTED_FILES) {
            Path pdfPath = sampleDirectory.resolve(fileName);

            assertTrue(
                    Files.isRegularFile(pdfPath),
                    "Missing sample PDF: " + pdfPath
            );

            byte[] pdfBytes = Files.readAllBytes(pdfPath);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    fileName,
                    "application/pdf",
                    pdfBytes
            );

            PdfExtractionResponse response = service.extract(file);

            assertTrue(
                    response.pageCount() > 0,
                    "PDF has no pages: " + fileName
            );
            assertTrue(
                    response.characterCount() > 0,
                    "PDF has no extracted text: " + fileName
            );
            assertFalse(
                    response.chunks().isEmpty(),
                    "PDF produced no chunks: " + fileName
            );

            totalPages += response.pageCount();
            totalCharacters += response.characterCount();
            totalChunks += response.chunks().size();

            System.out.println(
                    "\n--------------------------------------------------"
            );
            System.out.println("FILE: " + fileName);
            System.out.println("SIZE: " + pdfBytes.length + " bytes");
            System.out.println("PAGES: " + response.pageCount());
            System.out.println("CHARACTERS: " + response.characterCount());
            System.out.println("CHUNKS: " + response.chunks().size());

            System.out.println("\n--- PAGE TEXT PREVIEWS ---");

            response.pages().forEach(page -> {
                String preview = page.text()
                        .replaceAll("\\s+", " ")
                        .trim();

                if (preview.length() > 500) {
                    preview = preview.substring(0, 500) + "...";
                }

                System.out.printf(
                        "Page %d (%d chars): %s%n",
                        page.pageNumber(),
                        page.text().length(),
                        preview
                );
            });

            response.pages().forEach(page ->
                    printUnicodeDiagnostics(
                            fileName,
                            page.pageNumber(),
                            page.text()
                    )
            );

            System.out.println("\n--- CHUNK PREVIEWS ---");

            for (TextChunk chunk : response.chunks()) {
                String preview = chunk.content()
                        .replaceAll("\\s+", " ")
                        .trim();

                if (preview.length() > 1200) {
                    preview = preview.substring(0, 1200) + "...";
                }

                System.out.printf(
                        "Chunk %d | Page %d | %d chars | SHA-256: %s | %s%n",
                        chunk.chunkIndex(),
                        chunk.pageNumber(),
                        chunk.content().length(),
                        chunk.checksum(),
                        preview
                );
            }
        }

        System.out.println("\n========== TOTAL SUMMARY ==========");
        System.out.println("PDF files: " + EXPECTED_FILES.size());
        System.out.println("Total pages: " + totalPages);
        System.out.println("Total characters: " + totalCharacters);
        System.out.println("Total chunks: " + totalChunks);
        System.out.println(
                "Result: ALL SIX PDF FILES EXTRACTED SUCCESSFULLY"
        );
        System.out.println(
                "============================================================\n"
        );
    }

    private static void printUnicodeDiagnostics(
            String fileName,
            int pageNumber,
            String text
    ) {
        String[] suspiciousMarkers = {
                "\u00E2\u20AC\u2013",
                "\u00E2\u20AC\u00A2",
                "\u00E2\u20AC\u2122",
                "\u00E2\u20AC\u0153",
                "\u00E2\u20AC",
                "\u00E2\u2020\u2019"
        };

        String[] expectedUnicode = {
                "\u2013",
                "\u2022",
                "\u2019",
                "\u201C",
                "\u201D",
                "\u2192"
        };

        System.out.printf(
                "%n[UNICODE] %s | Page %d%n",
                fileName,
                pageNumber
        );

        for (String marker : suspiciousMarkers) {
            int index = text.indexOf(marker);

            if (index >= 0) {
                int start = Math.max(0, index - 12);
                int end = Math.min(
                        text.length(),
                        index + marker.length() + 12
                );

                printCodePoints(
                        "FOUND [" + marker + "]",
                        text.substring(start, end)
                );
            }
        }

        for (String marker : expectedUnicode) {
            long count = text.codePoints()
                    .filter(codePoint ->
                            codePoint == marker.codePointAt(0))
                    .count();

            if (count > 0) {
                System.out.printf(
                        "Expected character [%s]: %d%n",
                        marker,
                        count
                );
            }
        }
    }

    private static void printCodePoints(String label, String value) {
        StringBuilder result = new StringBuilder();

        value.codePoints().forEach(codePoint ->
                result.append(String.format("U+%04X ", codePoint))
        );

        System.out.println(label + " => " + result);
    }

    @Test
    void shouldPrintTextCoordinatesForScopeRolesPageFour()
            throws Exception {

        Path pdfPath = sampleDirectory.resolve("scope_roles.pdf");

        assertTrue(
                Files.isRegularFile(pdfPath),
                "Missing sample PDF: " + pdfPath
        );

        System.out.println(
                "\n========== PAGE 4 TEXT POSITION DIAGNOSTIC =========="
        );

        try (PDDocument document =
                     Loader.loadPDF(Files.readAllBytes(pdfPath))) {

            assertTrue(
                    document.getNumberOfPages() >= 4,
                    "scope_roles.pdf must contain at least 4 pages."
            );

            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(
                        String text,
                        List<TextPosition> textPositions
                ) throws IOException {

                    if (textPositions == null || textPositions.isEmpty()) {
                        return;
                    }

                    float minX = Float.MAX_VALUE;
                    float maxX = -Float.MAX_VALUE;
                    float minY = Float.MAX_VALUE;
                    float maxY = -Float.MAX_VALUE;

                    for (TextPosition position : textPositions) {
                        float x = position.getXDirAdj();
                        float y = position.getYDirAdj();

                        minX = Math.min(minX, x);
                        maxX = Math.max(
                                maxX,
                                x + position.getWidthDirAdj()
                        );
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }

                    String normalized = text
                            .replaceAll("\\s+", " ")
                            .trim();

                    System.out.printf(
                            Locale.ROOT,
                            "X=[%.1f, %.1f] Y=[%.1f, %.1f] chars=%d | %s%n",
                            minX,
                            maxX,
                            minY,
                            maxY,
                            textPositions.size(),
                            normalized
                    );
                }
            };

            stripper.setStartPage(4);
            stripper.setEndPage(4);
            stripper.setSortByPosition(true);
            stripper.getText(document);
        }

        System.out.println(
                "========== END PAGE 4 DIAGNOSTIC ==========\n"
        );
    }

    @Test
    void shouldInspectDrawingOperatorsOnScopeRolesPageFour()
            throws Exception {

        Path pdfPath = sampleDirectory.resolve("scope_roles.pdf");

        assertTrue(
                Files.isRegularFile(pdfPath),
                "Missing sample PDF: " + pdfPath
        );

        try (PDDocument document =
                     Loader.loadPDF(Files.readAllBytes(pdfPath))) {

            assertTrue(
                    document.getNumberOfPages() >= 4,
                    "scope_roles.pdf must contain at least 4 pages."
            );

            var page = document.getPage(3);

            System.out.println(
                    "\n========== PAGE 4 TRANSFORMED VECTOR PATHS =========="
            );

            PageFourGraphicsInspector inspector =
                    new PageFourGraphicsInspector(page);

            inspector.processPage(page);

            System.out.println(
                    "Total transformed paths: " + inspector.getPathCount()
            );

            System.out.println(
                    "========== END TRANSFORMED VECTOR PATHS ==========\n"
            );

            PDFStreamParser parser = new PDFStreamParser(page);
            List<Object> tokens = parser.parse();

            Map<String, Integer> operatorCounts = new TreeMap<>();

            for (Object token : tokens) {
                if (token instanceof Operator operator) {
                    operatorCounts.merge(
                            operator.getName(),
                            1,
                            Integer::sum
                    );
                }
            }

            System.out.println(
                    "\n========== PAGE 4 DRAWING DIAGNOSTIC =========="
            );

            operatorCounts.forEach((name, count) ->
                    System.out.println(name + " = " + count)
            );

            System.out.println("\n========== PAGE 4 VECTOR PATH DETAILS ==========");

            List<Object> pendingOperands = new ArrayList<>();
            int pathCommandCount = 0;

            for (Object token : tokens) {
                if (token instanceof Operator operator) {
                    String name = operator.getName();

                    boolean relevantOperator = List.of(
                            "m", "l", "c", "v", "y", "h", "re",
                            "S", "s", "f", "F", "f*", "B", "B*",
                            "b", "b*", "n", "cm", "q", "Q"
                    ).contains(name);

                    if (relevantOperator) {
                        String operands = pendingOperands.stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(" "));

                        System.out.printf(
                                Locale.ROOT,
                                "%03d | %-3s | %s%n",
                                ++pathCommandCount,
                                name,
                                operands
                        );
                    }

                    pendingOperands.clear();
                } else {
                    pendingOperands.add(token);
                }
            }

            System.out.println("Total vector commands: " + pathCommandCount);
            System.out.println("========== END VECTOR PATH DETAILS ==========\n");

            System.out.println(
                    "========== END DRAWING DIAGNOSTIC ==========\n"
            );
        }
    }

    @Test
    void shouldPrintReconstructedRaciTable() throws Exception {

        Path pdfPath = sampleDirectory.resolve("scope_roles.pdf");

        assertTrue(
                Files.isRegularFile(pdfPath),
                "Missing sample PDF: " + pdfPath
        );

        List<Segment> segments = new ArrayList<>();

        try (PDDocument document =
                     Loader.loadPDF(Files.readAllBytes(pdfPath))) {

            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(
                        String text,
                        List<TextPosition> positions
                ) throws IOException {

                    if (positions == null || positions.isEmpty()) {
                        return;
                    }

                    float minX = Float.MAX_VALUE;
                    float totalY = 0;

                    for (TextPosition position : positions) {
                        minX = Math.min(minX, position.getXDirAdj());
                        totalY += position.getYDirAdj();
                    }

                    String normalized = text
                            .replaceAll("\\s+", " ")
                            .trim();

                    if (!normalized.isEmpty()) {
                        segments.add(new Segment(
                                minX,
                                totalY / positions.size(),
                                normalized
                        ));
                    }
                }
            };

            stripper.setStartPage(4);
            stripper.setEndPage(4);
            stripper.setSortByPosition(true);
            stripper.getText(document);
        }

        List<Float> rowAnchors = new ArrayList<>();

        segments.stream()
                .filter(segment ->
                        segment.x() >= 180
                                && segment.y() >= 135
                                && segment.y() <= 270
                )
                .map(Segment::y)
                .sorted()
                .forEach(y -> {
                    if (rowAnchors.isEmpty()
                            || Math.abs(
                            y - rowAnchors.get(rowAnchors.size() - 1)
                    ) > 2.0f) {

                        rowAnchors.add(y);
                    }
                });

        List<String> actualRows = new ArrayList<>();

        for (float anchor : rowAnchors) {
            String[] cells = {"", "", "", "", ""};

            List<Segment> taskParts = segments.stream()
                    .filter(segment ->
                            segment.x() < 180
                                    && segment.y() >= anchor - 2.0f
                                    && segment.y() <= anchor + 16.5f
                    )
                    .sorted(
                            java.util.Comparator
                                    .comparingInt(
                                            (Segment segment) ->
                                                    Math.round(segment.y() / 2.0f)
                                    )
                                    .thenComparingDouble(Segment::x)
                    )
                    .toList();

            String task = taskParts.stream()
                    .map(Segment::text)
                    .collect(Collectors.joining(" "))
                    .replaceAll("-\\s+", "")
                    .replaceAll("\\s+", " ")
                    .trim();

            cells[0] = task;

            for (Segment segment : segments) {
                if (segment.x() < 180
                        || Math.abs(segment.y() - anchor) > 2.0f) {
                    continue;
                }

                int column;

                if (segment.x() < 260) {
                    column = 1;
                } else if (segment.x() < 345) {
                    column = 2;
                } else if (segment.x() < 428) {
                    column = 3;
                } else {
                    column = 4;
                }

                cells[column] = (
                        cells[column] + " " + segment.text()
                ).trim();
            }

            actualRows.add(String.join(" | ", cells));
        }

        System.out.println(
                "\n========== RECONSTRUCTED RACI TABLE =========="
        );
        System.out.println(
                "Task | Executives | Security Team | IT/DevOps | Developers"
        );
        actualRows.forEach(System.out::println);
        System.out.println(
                "========== END RECONSTRUCTED RACI TABLE ==========\n"
        );

        List<String> expectedRows = List.of(
                "Define policies | A | R | C | C",
                "Incident response | C | A/R | R | C",
                "Infrastructure hardening | C | C | A/R | C",
                "Secure coding | C | C | C | A/R",
                "Employee training | A | R | C | C"
        );

        assertEquals(
                expectedRows,
                actualRows,
                "RACI table reconstruction does not match the expected table."
        );
    }
}
