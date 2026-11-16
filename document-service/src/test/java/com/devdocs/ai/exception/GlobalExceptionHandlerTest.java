package com.devdocs.ai.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void validationErrorShouldReturn400WithFieldDetails() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");

        bindingResult.addError(
                new FieldError(
                        "request",
                        "title",
                        "",
                        false,
                        null,
                        null,
                        "Document title is required"
                )
        );

        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response =
                handler.handleValidation(exception);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(
                "Validation Failed",
                response.getBody().get("error")
        );

        @SuppressWarnings("unchecked")
        Map<String, String> details =
                (Map<String, String>) response.getBody().get("details");

        assertEquals(
                "Document title is required",
                details.get("title")
        );
    }

    @Test
    void invalidJsonShouldReturn400() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleInvalidJson();

        assertEquals(400, response.getStatusCode().value());
        assertEquals(
                "Request body is missing or contains invalid JSON",
                response.getBody().get("message")
        );
    }

    @Test
    void dataIntegrityViolationShouldReturn409() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrityViolation();

        assertEquals(409, response.getStatusCode().value());
        assertEquals(
                "The request conflicts with existing data",
                response.getBody().get("message")
        );
    }
}
