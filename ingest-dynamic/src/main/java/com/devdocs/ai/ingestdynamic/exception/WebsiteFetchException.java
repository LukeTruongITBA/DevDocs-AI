
package com.devdocs.ai.ingestdynamic.exception;

import java.io.IOException;

public class WebsiteFetchException extends IOException {

    public WebsiteFetchException(String message) {
        super(message);
    }

    public WebsiteFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
