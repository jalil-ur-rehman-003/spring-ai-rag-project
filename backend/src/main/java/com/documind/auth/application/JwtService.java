package com.documind.auth.application;

import com.documind.auth.infrastructure.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates short-lived JWT access tokens. Refresh tokens are a
 * separate, opaque, server-revocable mechanism (see RefreshTokenService) --
 * this class only ever deals with the stateless access token.
 */
@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private static final String CLAIM_ORGANIZATION_ID = "org_id";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final long accessTokenTtlMinutes;

    public JwtService(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.signingKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.accessTokenTtlMinutes = jwtProperties.accessTokenTtlMinutes();
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim(CLAIM_ORGANIZATION_ID, principal.getOrganizationId().toString())
                .claim(CLAIM_ROLE, principal.getUser().getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /** Parses and validates signature + expiry. Throws JwtException (caught upstream) on any invalid/expired/tampered token. */
    public AccessTokenClaims parseAndValidateAccessToken(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(CLAIM_ORGANIZATION_ID, String.class)),
                claims.get(CLAIM_ROLE, String.class)
        );
    }

    /** Minimal, already-validated view of an access token's claims, decoupled from jjwt's Claims type. */
    public record AccessTokenClaims(UUID userId, UUID organizationId, String role) {
    }
}
