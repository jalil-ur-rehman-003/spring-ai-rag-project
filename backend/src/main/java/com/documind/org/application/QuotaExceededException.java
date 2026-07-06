package com.documind.org.application;

/** Thrown when an operation would push an organization's storage usage over its configured quota. Mapped to HTTP 400 by GlobalExceptionHandler. */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }
}
