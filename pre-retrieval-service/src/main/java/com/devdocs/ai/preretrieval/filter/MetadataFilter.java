package com.devdocs.ai.preretrieval.filter;


import java.util.ArrayList;
import java.util.List;

/**
 * Validated metadata filter: a flat group of conditions joined by AND or OR.
 * Instances are produced only by {@link MetadataFilterParser}, so every field,
 * operator and value has already been whitelisted and type-converted.
 */
public record MetadataFilter(Mode mode, List<Condition> conditions) {

    public enum Mode { AND, OR }

    public record Condition(FilterField field, FilterOperator operator, List<Object> values) {
        public Condition {
            values = List.copyOf(values);
        }

        /** Human-readable form, e.g. {@code source_type eq WEB}. */
        public String describe() {
            List<String> rendered = values.stream().map(value ->
                    value instanceof java.sql.Timestamp timestamp
                            ? timestamp.toInstant().toString()
                            : String.valueOf(value).replaceAll("\\p{Cntrl}", "?")).toList();
            return field.fieldName() + " " + operator.token() + " " + String.join(",", rendered);
        }
    }

    public MetadataFilter {
        conditions = List.copyOf(conditions);
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("A metadata filter needs at least one condition.");
        }
    }

    /** Readable summary for responses and logs. */
    public List<String> describe() {
        return conditions.stream().map(Condition::describe).toList();
    }

    /**
     * Renders {@code ( cond1 AND|OR cond2 ... )} with {@code ?} placeholders;
     * the bound values are appended to {@code parameters} in order. Only enum
     * constants contribute SQL text.
     */
    public String toSql(List<Object> parameters) {
        List<String> fragments = new ArrayList<>();
        for (Condition condition : conditions) {
            String column = condition.field().sqlExpression();
            FilterOperator operator = condition.operator();
            switch (operator) {
                case IN -> {
                    String placeholders = String.join(", ",
                            java.util.Collections.nCopies(condition.values().size(), "?"));
                    fragments.add(column + " IN (" + placeholders + ")");
                    parameters.addAll(condition.values());
                }
                case PREFIX -> {
                    fragments.add(column + " LIKE ? ESCAPE '\\'");
                    parameters.add(escapeLike((String) condition.values().getFirst()) + "%");
                }
                case NE -> {
                    // NULL-safe: rows with no value are treated as "not equal".
                    fragments.add(column + " IS DISTINCT FROM ?");
                    parameters.add(condition.values().getFirst());
                }
                default -> {
                    fragments.add(column + " " + operator.sql() + " ?");
                    parameters.add(condition.values().getFirst());
                }
            }
        }
        return "(" + String.join(mode == Mode.AND ? " AND " : " OR ", fragments) + ")";
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
