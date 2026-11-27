package com.devdocs.ai.ingestdynamic.exception;

/**
 * Signals that an ingestion request would change an existing source version.
 *
 * Existing data is left untouched; the caller must choose an explicit repair
 * or migration operation if replacement is intended.
 */
public class IngestionConflictException extends RuntimeException {

    public IngestionConflictException(String message) {
        super(message);
    }
}
