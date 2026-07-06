package com.documind.document.application;

/** Thrown when an uploaded file fails input validation (wrong type or over the size limit). Mapped to HTTP 400 by GlobalExceptionHandler. */
public class UnsupportedDocumentFileException extends RuntimeException {

    public UnsupportedDocumentFileException(String message) {
        super(message);
    }
}
