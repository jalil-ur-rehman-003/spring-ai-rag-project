package com.documind.ingestion.domain;

import com.documind.document.domain.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per document ingestion attempt -- the async work queue. Workers
 * claim rows with "SELECT ... FOR UPDATE SKIP LOCKED" (see
 * IngestionJobScheduler) so multiple worker threads never double-process
 * the same job.
 */
@Entity
@Table(name = "ingestion_job")
public class IngestionJob {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IngestionJob() {
        // required by JPA
    }

    public static IngestionJob createPendingFor(Document document) {
        IngestionJob job = new IngestionJob();
        job.id = UUID.randomUUID();
        job.document = document;
        job.status = IngestionJobStatus.PENDING;
        job.attemptCount = 0;
        return job;
    }

    public void markRunning() {
        this.status = IngestionJobStatus.RUNNING;
        this.attemptCount += 1;
    }

    public void markSucceeded() {
        this.status = IngestionJobStatus.SUCCEEDED;
    }

    public void markFailed(String reason) {
        this.status = IngestionJobStatus.FAILED;
        this.lastError = reason;
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

    public Document getDocument() {
        return document;
    }

    public IngestionJobStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
