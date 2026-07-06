package com.documind.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Only the SHA-256 hash of the raw refresh token is ever persisted (see
 * auth.application.RefreshTokenService) — a leaked database dump must not
 * hand out reusable credentials. Revocation is a column update rather than
 * deleting the row, so a revoked token's usage attempt can still be audited.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // required by JPA
    }

    public static RefreshToken issueFor(User user, String tokenHash, Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.id = UUID.randomUUID();
        refreshToken.user = user;
        refreshToken.tokenHash = tokenHash;
        refreshToken.expiresAt = expiresAt;
        return refreshToken;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
