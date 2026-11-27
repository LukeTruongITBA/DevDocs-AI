package com.devdocs.ai.preretrieval.filter;

import java.util.List;

/** Raised when URL filter parameters fail validation; mapped to HTTP 400. */
public class InvalidFilterException extends RuntimeException {

    private final List<String> errors;

    public InvalidFilterException(List<String> errors) {
        super("Invalid metadata filter: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
