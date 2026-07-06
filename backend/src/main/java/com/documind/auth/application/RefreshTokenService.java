package com.documind.auth.application;

import com.documind.auth.domain.RefreshToken;
import com.documind.auth.domain.User;
import com.documind.auth.infrastructure.RefreshTokenRepository;
import com.documind.common.error.AuthenticationFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues opaque refresh tokens and stores only their SHA-256 hash, so a
 * leaked database dump cannot be replayed as a valid credential. Rotation
 * (revoke-old, issue-new) happens on every refresh call to limit the window
 * in which a stolen refresh token remains useful.
 */
@Service
public class RefreshTokenService {

    private static final int RAW_TOKEN_BYTE_LENGTH = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshTokenTtlDays;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${documind.security.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    /** Generates a new raw refresh token, persists only its hash, and returns the raw value to hand back to the client exactly once. */
    public String issueRawTokenFor(User user) {
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(refreshTokenTtlDays, ChronoUnit.DAYS);
        RefreshToken refreshToken = RefreshToken.issueFor(user, hash(rawToken), expiresAt);
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    /** Validates a raw refresh token presented by a client, returning the owning User if it is usable (not expired, not revoked). */
    public User validateAndGetOwner(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AuthenticationFailedException("Refresh token is invalid"));

        if (!refreshToken.isUsable()) {
            throw new AuthenticationFailedException("Refresh token is expired or has been revoked");
        }

        return refreshToken.getUser();
    }

    /** Revokes the refresh token matching this raw value, used on logout and on rotation during refresh. */
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(refreshToken -> {
                    refreshToken.revoke();
                    refreshTokenRepository.save(refreshToken);
                });
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[RAW_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available on every supported JVM", exception);
        }
    }
}
