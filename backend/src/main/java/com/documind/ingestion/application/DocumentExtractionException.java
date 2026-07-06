package com.documind.ingestion.application;

/** Thrown when a document's content cannot be extracted (corrupt file, unsupported encoding, parser failure). */
public class DocumentExtractionException extends RuntimeException {

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
