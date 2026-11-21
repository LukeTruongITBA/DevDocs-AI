
package com.devdocs.ai.preretrieval.query.service;

import com.devdocs.ai.preretrieval.query.dto.QueryNormalizationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryNormalizationServiceTest {

    private QueryNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new QueryNormalizationService(new SynonymDictionary());
    }

    @Test
    void shouldNormalizeCaseAndWhitespace() {
        QueryNormalizationResponse response = service.normalize(
                "  What   is SPRING   BOOT?  "
        );

        assertEquals(
                "  What   is SPRING   BOOT?  ",
                response.originalQuery()
        );
        assertEquals(
                "what is spring boot?",
                response.normalizedQuery()
        );
        assertTrue(response.synonymTerms().isEmpty());
    }

    @Test
    void shouldRecognizeIocSynonym() {
        QueryNormalizationResponse response = service.normalize(
                "What is IOC?"
        );

        assertEquals("what is ioc?", response.normalizedQuery());
        assertTrue(
                response.synonymTerms().contains("inversion of control")
        );
    }

    @Test
    void shouldRecognizeDependencyInjectionSynonym() {
        QueryNormalizationResponse response = service.normalize(
                "Explain DI in Spring"
        );

        assertTrue(
                response.synonymTerms().contains("dependency injection")
        );
    }

    @Test
    void shouldNotMatchKeywordInsideAnotherWord() {
        QueryNormalizationResponse response = service.normalize(
                "digital transformation"
        );

        assertTrue(response.synonymTerms().isEmpty());
    }

    @Test
    void shouldRejectNullQuery() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.normalize(null)
        );
    }

    @Test
    void shouldRejectBlankQuery() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.normalize("   ")
        );
    }
}
