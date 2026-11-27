package com.devdocs.ai.preretrieval.filter;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.devdocs.ai.preretrieval.filter.FilterOperator.EQ;
import static com.devdocs.ai.preretrieval.filter.FilterOperator.GT;
import static com.devdocs.ai.preretrieval.filter.FilterOperator.GTE;
import static com.devdocs.ai.preretrieval.filter.FilterOperator.IN;
import static com.devdocs.ai.preretrieval.filter.FilterOperator.LT;
import static com.devdocs.ai.preretrieval.filter.FilterOperator.LTE;
import static com.devdocs.ai.preretrieval.filter.FilterOperator.NE;
import static com.devdocs.ai.preretrieval.filter.FilterOperator.PREFIX;

/**
 * Whitelisted filterable metadata fields. Each maps to a compile-time SQL
 * expression over the aliases {@code c} (chunks), {@code dv}
 * (document_versions) and {@code d} (documents). Request input never
 * becomes part of the SQL text; values are always bound parameters.
 */
public enum FilterField {

    SOURCE_TYPE("source_type", "d.source_type", ValueType.SOURCE_TYPE,
            EnumSet.of(EQ, NE, IN)),
    DOCUMENT_ID("document_id", "d.id", ValueType.LONG,
            EnumSet.of(EQ, NE, IN)),
    VERSION_ID("version_id", "c.version_id", ValueType.LONG,
            EnumSet.of(EQ, NE, IN)),
    VERSION("version", "dv.version_label", ValueType.STRING,
            EnumSet.of(EQ, NE, IN)),
    IS_CURRENT("is_current", "dv.is_current", ValueType.BOOLEAN,
            EnumSet.of(EQ)),
    SOURCE("source", "d.source_uri", ValueType.STRING,
            EnumSet.of(EQ, NE, IN, PREFIX)),
    /** Static PDFs store the page in page_number; remote PDFs in metadata.page.pdf_page_number. */
    PAGE("page", "COALESCE(c.page_number, (c.metadata->'page'->>'pdf_page_number')::int)", ValueType.POSITIVE_INT,
            EnumSet.of(EQ, NE, GT, GTE, LT, LTE, IN)),
    FETCH_DATE("fetch_date", "(c.metadata->>'fetch_date')::timestamptz", ValueType.INSTANT,
            EnumSet.of(GT, GTE, LT, LTE));

    static final int MAX_STRING_LENGTH = 2048;

    private final String name;
    private final String sqlExpression;
    private final ValueType valueType;
    private final Set<FilterOperator> operators;

    FilterField(String name, String sqlExpression, ValueType valueType,
                Set<FilterOperator> operators) {
        this.name = name;
        this.sqlExpression = sqlExpression;
        this.valueType = valueType;
        this.operators = operators;
    }

    public String fieldName() {
        return name;
    }

    String sqlExpression() {
        return sqlExpression;
    }

    public Set<FilterOperator> operators() {
        return operators;
    }

    public String valueDescription() {
        return valueType.description;
    }

    public static Optional<FilterField> fromName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (FilterField field : values()) {
            if (field.name.equals(normalized)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    /** Converts one raw value to its typed form, or throws with a safe message. */
    Object convert(String raw) {
        return valueType.convert(raw);
    }

    enum ValueType {
        STRING("non-empty string up to " + MAX_STRING_LENGTH + " characters") {
            @Override
            Object convert(String raw) {
                if (raw.isEmpty() || raw.length() > MAX_STRING_LENGTH) {
                    throw new IllegalArgumentException("expected " + description);
                }
                return raw;
            }
        },
        LONG("positive integer") {
            @Override
            Object convert(String raw) {
                try {
                    long value = Long.parseLong(raw);
                    if (value <= 0) {
                        throw new NumberFormatException();
                    }
                    return value;
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("expected " + description);
                }
            }
        },
        POSITIVE_INT("positive integer") {
            @Override
            Object convert(String raw) {
                try {
                    int value = Integer.parseInt(raw);
                    if (value <= 0) {
                        throw new NumberFormatException();
                    }
                    return value;
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("expected " + description);
                }
            }
        },
        BOOLEAN("true or false") {
            @Override
            Object convert(String raw) {
                return switch (raw.toLowerCase(Locale.ROOT)) {
                    case "true" -> Boolean.TRUE;
                    case "false" -> Boolean.FALSE;
                    default -> throw new IllegalArgumentException("expected " + description);
                };
            }
        },
        SOURCE_TYPE("STATIC or WEB") {
            @Override
            Object convert(String raw) {
                String upper = raw.toUpperCase(Locale.ROOT);
                if (!upper.equals("STATIC") && !upper.equals("WEB")) {
                    throw new IllegalArgumentException("expected " + description);
                }
                return upper;
            }
        },
        INSTANT("ISO-8601 UTC timestamp, e.g. 2026-11-27T00:00:00Z") {
            @Override
            Object convert(String raw) {
                try {
                    return java.sql.Timestamp.from(Instant.parse(raw));
                } catch (DateTimeParseException exception) {
                    throw new IllegalArgumentException("expected " + description);
                }
            }
        };

        final String description;

        ValueType(String description) {
            this.description = description;
        }

        abstract Object convert(String raw);
    }
}
