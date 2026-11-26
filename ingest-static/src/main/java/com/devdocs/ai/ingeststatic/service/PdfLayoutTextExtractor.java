package com.devdocs.ai.ingeststatic.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Extracts positioned text while keeping table rows and column order visible. */
@Component
public class PdfLayoutTextExtractor {

    private static final float ROW_TOLERANCE = 2.5f;
    private static final float CELL_GAP = 30.0f;
    private static final float COLUMN_GAP = 36.0f;

    public String extractPage(PDDocument document, int pageNumber)
            throws IOException {
        PositionedStripper stripper = new PositionedStripper();
        stripper.setSortByPosition(true);
        stripper.setSuppressDuplicateOverlappingText(true);
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        stripper.getText(document);
        return formatRows(stripper.lines);
    }

    private String formatRows(List<PositionedLine> lines) {
        List<Row> rows = groupRows(lines);
        Map<Integer, List<Boundary>> boundaries = new TreeMap<>();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<Segment> segments = rows.get(rowIndex).segments();
            for (int i = 1; i < segments.size(); i++) {
                Segment left = segments.get(i - 1);
                Segment right = segments.get(i);
                float gap = right.startX() - left.endX();
                if (gap >= COLUMN_GAP) {
                    int bucket = Math.round(right.startX() / 20);
                    boundaries.computeIfAbsent(bucket, ignored -> new ArrayList<>())
                            .add(new Boundary(rowIndex, right.startX()));
                }
            }
        }

        List<ColumnBoundary> stableBoundaries = boundaries.entrySet().stream()
                .map(entry -> new ColumnBoundary(
                        (float) entry.getValue().stream()
                                .mapToDouble(Boundary::rightStartX)
                                .average().orElse(entry.getKey() * 20.0),
                        entry.getValue().stream()
                                .map(Boundary::rowIndex)
                                .distinct()
                                .count()
                ))
                .filter(boundary -> boundary.rowCount() >= 3)
                .toList();

        if (stableBoundaries.size() == 1) {
            return formatTwoColumnFlow(rows, stableBoundaries.getFirst().x());
        }

        return rows.stream()
                .map(row -> row.segments().stream()
                        .map(Segment::text)
                        .reduce((left, right) -> left + " | " + right)
                        .orElse(""))
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatTwoColumnFlow(List<Row> rows, float splitX) {
        List<Integer> columnRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            boolean hasTextOnBothSides = rows.get(i).segments().stream()
                    .anyMatch(segment -> segment.endX() <= splitX)
                    && rows.get(i).segments().stream()
                    .anyMatch(segment -> segment.startX() >= splitX);
            if (hasTextOnBothSides) {
                columnRows.add(i);
            }
        }

        if (columnRows.size() < 3) {
            return formatRowsAsLines(rows);
        }

        int first = columnRows.getFirst();
        int last = columnRows.getLast();
        List<String> output = new ArrayList<>();
        for (int i = 0; i < first; i++) {
            output.add(formatRow(rows.get(i)));
        }

        for (int i = first; i <= last; i++) {
            String text = rows.get(i).segments().stream()
                    .filter(segment -> segment.endX() <= splitX)
                    .map(Segment::text)
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
            if (!text.isBlank()) output.add(text);
        }
        output.add("");
        for (int i = first; i <= last; i++) {
            String text = rows.get(i).segments().stream()
                    .filter(segment -> segment.startX() >= splitX)
                    .map(Segment::text)
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
            if (!text.isBlank()) output.add(text);
        }
        for (int i = last + 1; i < rows.size(); i++) {
            output.add(formatRow(rows.get(i)));
        }

        return String.join("\n", output).trim();
    }

    private String formatRowsAsLines(List<Row> rows) {
        return rows.stream().map(this::formatRow)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatRow(Row row) {
        return row.segments().stream().map(Segment::text)
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    private List<Row> groupRows(List<PositionedLine> lines) {
        List<PositionedLine> sorted = lines.stream()
                .sorted(Comparator.comparingDouble(PositionedLine::y)
                        .thenComparingDouble(PositionedLine::x))
                .toList();
        List<Row> rows = new ArrayList<>();
        for (PositionedLine line : sorted) {
            Row row = rows.isEmpty() ? null : rows.getLast();
            if (row == null || Math.abs(row.y() - line.y()) > ROW_TOLERANCE) {
                row = new Row(line.y(), new ArrayList<>());
                rows.add(row);
            }
            row.segments().addAll(line.segments());
        }
        rows.forEach(row -> {
            row.segments().sort(Comparator.comparingDouble(Segment::startX));
            mergeAdjacentWords(row.segments());
        });
        return rows;
    }

    private void mergeAdjacentWords(List<Segment> segments) {
        if (segments.size() < 2) return;
        List<Segment> merged = new ArrayList<>();
        for (Segment segment : segments) {
            if (merged.isEmpty()) {
                merged.add(segment);
                continue;
            }
            Segment previous = merged.getLast();
            float gap = segment.startX() - previous.endX();
            if (gap < CELL_GAP) {
                merged.set(merged.size() - 1, new Segment(
                        (previous.text() + " " + segment.text()).trim(),
                        previous.startX(),
                        Math.max(previous.endX(), segment.endX())
                ));
            } else {
                merged.add(segment);
            }
        }
        segments.clear();
        segments.addAll(merged);
    }

    private static final class PositionedStripper extends PDFTextStripper {
        private final List<PositionedLine> lines = new ArrayList<>();

        private PositionedStripper() throws IOException {
            setLineSeparator("\n");
            setWordSeparator(" ");
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            if (positions == null || positions.isEmpty()) return;
            List<Segment> segments = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            float startX = positions.getFirst().getXDirAdj();
            float previousEnd = startX;
            float yTotal = 0;
            int yCount = 0;

            for (TextPosition position : positions) {
                String unicode = position.getUnicode();
                if (unicode == null || unicode.isEmpty()) continue;
                float x = position.getXDirAdj();
                float width = position.getWidthDirAdj();
                float gap = x - previousEnd;
                if (current.length() > 0 && gap >= CELL_GAP) {
                    addSegment(segments, current, startX, previousEnd);
                    current = new StringBuilder();
                    startX = x;
                } else if (current.length() > 0 && gap > 1.5f
                        && !Character.isWhitespace(current.charAt(current.length() - 1))) {
                    current.append(' ');
                }
                current.append(unicode);
                previousEnd = Math.max(previousEnd, x + width);
                yTotal += position.getYDirAdj();
                yCount++;
            }
            addSegment(segments, current, startX, previousEnd);
            if (!segments.isEmpty()) {
                lines.add(new PositionedLine(
                        yTotal / Math.max(1, yCount),
                        segments.getFirst().startX(),
                        segments
                ));
            }
        }

        private void addSegment(List<Segment> segments, StringBuilder text,
                                float startX, float endX) {
            String value = text.toString().trim();
            if (!value.isEmpty()) segments.add(new Segment(value, startX, endX));
        }
    }

    private record PositionedLine(float y, float x, List<Segment> segments) { }
    private record Segment(String text, float startX, float endX) { }
    private record Boundary(int rowIndex, float rightStartX) { }
    private record ColumnBoundary(float x, long rowCount) { }
    private record Row(float y, List<Segment> segments) { }
}
