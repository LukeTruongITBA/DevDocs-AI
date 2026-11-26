package com.devdocs.ai.ingeststatic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidPdfException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPdf(
            InvalidPdfException exception
    ) {
        log.warn("Rejected PDF upload: errorType={}", exception.getClass().getSimpleName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Invalid PDF",
                        "message", exception.getMessage()
                ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleRejectedIngestion(
            ResponseStatusException exception
    ) {
        log.warn("PDF ingestion request rejected: status={}, errorType={}",
                exception.getStatusCode().value(), exception.getClass().getSimpleName());
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of(
                        "error", "PDF ingestion rejected",
                        "message", exception.getReason() == null
                                ? "Request could not be accepted."
                                : exception.getReason()
                ));
    }
}
