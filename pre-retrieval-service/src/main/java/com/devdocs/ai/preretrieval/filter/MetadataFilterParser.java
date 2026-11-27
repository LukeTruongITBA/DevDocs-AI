package com.devdocs.ai.preretrieval.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses URL filter parameters into a validated {@link MetadataFilter}.
 *
 * <p>Syntax: repeated {@code filter=<field>:<operator>:<value>} parameters,
 * combined by {@code filterMode=AND} (default) or {@code filterMode=OR}.
 * For {@code in}, the value is a comma-separated list. The value is
 * everything after the second colon, so values may contain colons (URLs,
 * timestamps).
 *
 * <p>All problems are collected and reported together. No filter parameters
 * (null or empty) yields {@code null}, meaning "use the standard flow".
 */
public final class MetadataFilterParser {

    public static final int MAX_CONDITIONS = 10;
    public static final int MAX_IN_VALUES = 20;
    private static final int MAX_RAW_LENGTH = 4096;

    private MetadataFilterParser() {
    }

    public static MetadataFilter parse(List<String> rawFilters, String rawMode) {
        boolean noFilters = rawFilters == null
                || rawFilters.stream().allMatch(raw -> raw == null || raw.isBlank());
        if (noFilters) {
            if (rawMode != null && !rawMode.isBlank()) {
                throw new InvalidFilterException(List.of(
                        "filterMode was given without any filter parameter"));
            }
            return null;
        }

        List<String> errors = new ArrayList<>();
        MetadataFilter.Mode mode = parseMode(rawMode, errors);

        List<String> nonBlank = rawFilters.stream()
                .filter(raw -> raw != null && !raw.isBlank()).toList();
        if (nonBlank.size() > MAX_CONDITIONS) {
            errors.add("at most " + MAX_CONDITIONS + " filter conditions are allowed, got "
                    + nonBlank.size());
        }

        List<MetadataFilter.Condition> conditions = new ArrayList<>();
        for (int i = 0; i < nonBlank.size() && i < MAX_CONDITIONS; i++) {
            MetadataFilter.Condition condition = parseCondition(nonBlank.get(i), i + 1, errors);
            if (condition != null) {
                conditions.add(condition);
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidFilterException(errors);
        }
        return new MetadataFilter(mode, conditions);
    }

    /**
     * Wraps raw servlet parameter values. Controllers must read
     * {@code request.getParameterValues("filter")} rather than binding
     * {@code List<String>}/{@code String[]}: Spring's conversion splits a
     * single parameter on commas, which would break {@code in} lists and
     * values containing commas.
     */
    public static List<String> rawValues(String[] parameterValues) {
        return parameterValues == null ? null : List.of(parameterValues);
    }

    /** Whitelist description returned with every 400: field -> operators and value type. */
    public static Map<String, Map<String, Object>> validFields() {
        Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        for (FilterField field : FilterField.values()) {
            Map<String, Object> description = new LinkedHashMap<>();
            description.put("operators", field.operators().stream()
                    .map(FilterOperator::token).toList());
            description.put("value", field.valueDescription());
            fields.put(field.fieldName(), description);
        }
        return fields;
    }

    private static MetadataFilter.Mode parseMode(String rawMode, List<String> errors) {
        if (rawMode == null || rawMode.isBlank()) {
            return MetadataFilter.Mode.AND;
        }
        return switch (rawMode.trim().toUpperCase(Locale.ROOT)) {
            case "AND" -> MetadataFilter.Mode.AND;
            case "OR" -> MetadataFilter.Mode.OR;
            default -> {
                errors.add("filterMode must be AND or OR");
                yield MetadataFilter.Mode.AND;
            }
        };
    }

    private static MetadataFilter.Condition parseCondition(
            String raw, int position, List<String> errors) {
        String label = "filter #" + position;
        if (raw.length() > MAX_RAW_LENGTH) {
            errors.add(label + ": longer than " + MAX_RAW_LENGTH + " characters");
            return null;
        }

        int first = raw.indexOf(':');
        int second = first < 0 ? -1 : raw.indexOf(':', first + 1);
        if (first <= 0 || second < 0) {
            errors.add(label + ": expected <field>:<operator>:<value>");
            return null;
        }

        String rawField = raw.substring(0, first);
        String rawOperator = raw.substring(first + 1, second);
        String rawValue = raw.substring(second + 1).trim();

        FilterField field = FilterField.fromName(rawField).orElse(null);
        if (field == null) {
            errors.add(label + ": unknown field '" + safe(rawField) + "'");
            return null;
        }
        FilterOperator operator = FilterOperator.fromToken(rawOperator).orElse(null);
        if (operator == null || !field.operators().contains(operator)) {
            errors.add(label + ": operator '" + safe(rawOperator) + "' is not allowed for "
                    + field.fieldName() + "; allowed: "
                    + field.operators().stream().map(FilterOperator::token).toList());
            return null;
        }

        List<String> rawValues = operator == FilterOperator.IN
                ? List.of(rawValue.split(",", -1))
                : List.of(rawValue);
        if (operator == FilterOperator.IN && rawValues.size() > MAX_IN_VALUES) {
            errors.add(label + ": at most " + MAX_IN_VALUES + " values are allowed for in");
            return null;
        }

        List<Object> values = new ArrayList<>();
        for (String value : rawValues) {
            try {
                values.add(field.convert(value.trim()));
            } catch (IllegalArgumentException exception) {
                errors.add(label + ": invalid value for " + field.fieldName() + ", "
                        + exception.getMessage());
                return null;
            }
        }
        return new MetadataFilter.Condition(field, operator, values);
    }

    /** Echo at most 40 printable characters of user input in error messages. */
    private static String safe(String raw) {
        String printable = raw.replaceAll("[^\\x20-\\x7E]", "?").trim();
        return printable.length() > 40 ? printable.substring(0, 40) + "..." : printable;
    }
}
