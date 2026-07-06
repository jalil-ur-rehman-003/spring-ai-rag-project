package com.documind.common.error;

import java.time.Instant;
import java.util.List;

/**
 * RFC 7807 "problem detail" response body. Every error response the API
 * returns (validation, not-found, auth, access-denied, unexpected failures)
 * is shaped as one of these via {@link GlobalExceptionHandler}, so API
 * consumers never have to branch on multiple error body shapes.
 */
public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Instant timestamp,
        List<ValidationError> validationErrors
) {

    public static ApiError of(String title, int status, String detail, String instance) {
        return new ApiError("about:blank", title, status, detail, instance, Instant.now(), List.of());
    }

    public static ApiError withValidationErrors(
            String title, int status, String detail, String instance, List<ValidationError> validationErrors
    ) {
        return new ApiError("about:blank", title, status, detail, instance, Instant.now(), validationErrors);
    }

    /** One field-level validation failure, as reported by jakarta.validation. */
    public record ValidationError(String field, String message) {
    }
}
