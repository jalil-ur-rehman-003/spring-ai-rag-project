package com.documind.org.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The tenant boundary: every user, document, and chat session belongs to exactly one organization. */
@Entity
@Table(name = "organization")
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false)
    private PlanTier planTier;

    @Column(name = "storage_quota_bytes", nullable = false)
    private long storageQuotaBytes;

    @Column(name = "storage_used_bytes", nullable = false)
    private long storageUsedBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Organization() {
        // required by JPA
    }

    public static Organization createNew(String name, PlanTier planTier, long storageQuotaBytes) {
        Organization organization = new Organization();
        organization.id = UUID.randomUUID();
        organization.name = name;
        organization.planTier = planTier;
        organization.storageQuotaBytes = storageQuotaBytes;
        organization.storageUsedBytes = 0;
        return organization;
    }

    /**
     * Reserves additionalBytes against this organization's quota, throwing if
     * doing so would exceed it. Called before a document is actually stored
     * (see DocumentUploadService) so a rejected upload never gets counted.
     */
    public void reserveStorage(long additionalBytes) {
        if (storageUsedBytes + additionalBytes > storageQuotaBytes) {
            throw new com.documind.org.application.QuotaExceededException(
                    "Uploading this file would exceed the organization's storage quota of " + storageQuotaBytes + " bytes"
            );
        }
        this.storageUsedBytes += additionalBytes;
    }

    public void releaseStorage(long bytesToRelease) {
        this.storageUsedBytes = Math.max(0, this.storageUsedBytes - bytesToRelease);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PlanTier getPlanTier() {
        return planTier;
    }

    public long getStorageQuotaBytes() {
        return storageQuotaBytes;
    }

    public long getStorageUsedBytes() {
        return storageUsedBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
