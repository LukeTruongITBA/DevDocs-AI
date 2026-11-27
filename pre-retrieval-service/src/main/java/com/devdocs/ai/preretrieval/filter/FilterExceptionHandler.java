package com.devdocs.ai.preretrieval.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps invalid URL filters to HTTP 400 with the list of valid fields. */
@RestControllerAdvice
public class FilterExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FilterExceptionHandler.class);

    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFilter(InvalidFilterException exception) {
        log.warn("Rejected invalid metadata filter: errorCount={}, errors={}",
                exception.errors().size(), exception.errors());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Invalid metadata filter");
        body.put("details", exception.errors());
        body.put("syntax", "filter=<field>:<operator>:<value> (repeatable); "
                + "filterMode=AND|OR (default AND); 'in' takes comma-separated values");
        body.put("validFields", MetadataFilterParser.validFields());
        body.put("filterModes", List.of("AND", "OR"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
