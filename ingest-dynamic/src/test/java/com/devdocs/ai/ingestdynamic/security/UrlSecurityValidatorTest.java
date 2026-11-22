
package com.devdocs.ai.ingestdynamic.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlSecurityValidatorTest {

    private UrlSecurityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UrlSecurityValidator();
    }

    @Test
    void shouldAcceptPublicHttpsUrl() {
        assertDoesNotThrow(() ->
                validator.validate("https://docs.spring.io/spring-boot/")
        );
    }

    @Test
    void shouldAcceptPublicHttpUrl() {
        assertDoesNotThrow(() ->
                validator.validate("http://example.com/docs")
        );
    }

    @Test
    void shouldRejectNullUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(null));
    }

    @Test
    void shouldRejectBlankUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("   "));
    }

    @Test
    void shouldRejectMalformedUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("not-a-valid-url"));
    }

    @Test
    void shouldRejectUnsupportedScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("ftp://example.com/docs"));
    }

    @Test
    void shouldRejectUrlWithoutHost() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("https:///docs"));
    }

    @Test
    void shouldRejectEmbeddedCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("https://user:password@example.com"));
    }

    @Test
    void shouldRejectLocalhost() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://localhost:8080"));
    }

    @Test
    void shouldRejectLoopbackIpv4() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://127.0.0.1"));
    }

    @Test
    void shouldRejectPrivateIpv4() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://192.168.1.10"));
    }

    @Test
    void shouldRejectPrivateIpv4In172Range() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://172.16.0.10"));
    }

    @Test
    void shouldRejectLinkLocalIpv4() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://169.254.10.20"));
    }

    @Test
    void shouldRejectPrivateIpv6() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://[fc00::1]"));
    }

    @Test
    void shouldRejectDocumentationIpv4() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://192.0.2.10"));
    }
}
