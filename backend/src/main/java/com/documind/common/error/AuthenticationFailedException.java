package com.documind.common.error;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown for domain-level authentication failures (bad credentials, expired
 * or revoked refresh token) that should surface as HTTP 401. Extends Spring
 * Security's {@link AuthenticationException} so it flows through the same
 * exception-handling path Spring Security already understands.
 */
public class AuthenticationFailedException extends AuthenticationException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
