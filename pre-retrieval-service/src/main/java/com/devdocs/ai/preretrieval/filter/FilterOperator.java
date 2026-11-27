package com.devdocs.ai.preretrieval.filter;

import java.util.Locale;
import java.util.Optional;

/** Whitelisted comparison operators. The SQL token is a constant. */
public enum FilterOperator {

    EQ("eq", "="),
    NE("ne", "<>"),
    GT("gt", ">"),
    GTE("gte", ">="),
    LT("lt", "<"),
    LTE("lte", "<="),
    /** Comma-separated list of values. */
    IN("in", "IN"),
    /** String starts-with; the value is LIKE-escaped and bound. */
    PREFIX("prefix", "LIKE");

    private final String token;
    private final String sql;

    FilterOperator(String token, String sql) {
        this.token = token;
        this.sql = sql;
    }

    public String token() {
        return token;
    }

    String sql() {
        return sql;
    }

    public static Optional<FilterOperator> fromToken(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (FilterOperator operator : values()) {
            if (operator.token.equals(normalized)) {
                return Optional.of(operator);
            }
        }
        return Optional.empty();
    }
}
