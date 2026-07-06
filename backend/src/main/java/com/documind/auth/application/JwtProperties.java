package com.documind.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds documind.security.jwt.* from application.yml. signingKey must be a
 * long, random, base64-safe secret supplied via the JWT_SIGNING_KEY
 * environment variable in every environment — never hardcoded.
 */
@ConfigurationProperties(prefix = "documind.security.jwt")
public record JwtProperties(
        String signingKey,
        long accessTokenTtlMinutes,
        long refreshTokenTtlDays
) {
}
