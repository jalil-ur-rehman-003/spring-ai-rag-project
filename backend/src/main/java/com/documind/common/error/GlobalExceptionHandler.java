package com.documind.common.error;

import com.documind.document.application.UnsupportedDocumentFileException;
import com.documind.guardrail.domain.GuardrailType;
import com.documind.guardrail.domain.GuardrailViolationException;
import com.documind.org.application.QuotaExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * Single point of translation from exceptions to RFC 7807 problem-detail
 * responses. Kept centralized so controllers never need their own try/catch
 * blocks for these common failure modes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationFailure(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiError.ValidationError> validationErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiError.ValidationError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ApiError body = ApiError.withValidationErrors(
                "Validation Failed", HttpStatus.BAD_REQUEST.value(),
                "One or more fields failed validation", request.getRequestURI(), validationErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException exception, HttpServletRequest request) {
        ApiError body = ApiError.of("Not Found", HttpStatus.NOT_FOUND.value(), exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailure(AuthenticationException exception, HttpServletRequest request) {
        ApiError body = ApiError.of("Authentication Failed", HttpStatus.UNAUTHORIZED.value(), exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        ApiError body = ApiError.of("Access Denied", HttpStatus.FORBIDDEN.value(), exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(UnsupportedDocumentFileException.class)
    public ResponseEntity<ApiError> handleUnsupportedDocumentFile(UnsupportedDocumentFileException exception, HttpServletRequest request) {
        ApiError body = ApiError.of("Unsupported File", HttpStatus.BAD_REQUEST.value(), exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        ApiError body = ApiError.of(
                "Unsupported File", HttpStatus.BAD_REQUEST.value(), "Uploaded file exceeds the maximum allowed size", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiError> handleQuotaExceeded(QuotaExceededException exception, HttpServletRequest request) {
        ApiError body = ApiError.of("Quota Exceeded", HttpStatus.BAD_REQUEST.value(), exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(GuardrailViolationException.class)
    public ResponseEntity<ApiError> handleGuardrailViolation(GuardrailViolationException exception, HttpServletRequest request) {
        HttpStatus status = exception.getGuardrailType() == GuardrailType.RATE_LIMIT_EXCEEDED
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.BAD_REQUEST;
        ApiError body = ApiError.of("Guardrail Violation", status.value(), exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedFailure(Exception exception, HttpServletRequest request) {
        // The client only ever sees a generic message (avoids leaking internals), but the full
        // exception must still be logged -- otherwise an unexpected failure is undiagnosable.
        logger.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), exception);

        ApiError body = ApiError.of(
                "Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
