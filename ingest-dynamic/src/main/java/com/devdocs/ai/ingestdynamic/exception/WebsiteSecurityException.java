
package com.devdocs.ai.ingestdynamic.exception;

public class WebsiteSecurityException extends IllegalArgumentException {

    public WebsiteSecurityException(String message) {
        super(message);
    }

    public WebsiteSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
