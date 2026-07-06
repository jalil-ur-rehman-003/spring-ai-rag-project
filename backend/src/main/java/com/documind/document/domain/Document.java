package com.documind.document.domain;

import com.documind.auth.domain.User;
import com.documind.org.domain.Organization;
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

/** Metadata record for an uploaded file. Raw bytes live in object storage; storageKey is the pointer into that bucket. */
@Entity
@Table(name = "document")
public class Document {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {
        // required by JPA
    }

    public static Document createPending(
            Organization organization, User uploadedBy, String originalFilename,
            String storageKey, String contentType, long sizeBytes, DocumentVisibility visibility
    ) {
        Document document = new Document();
        document.id = UUID.randomUUID();
        document.organization = organization;
        document.uploadedBy = uploadedBy;
        document.title = originalFilename;
        document.originalFilename = originalFilename;
        document.storageKey = storageKey;
        document.contentType = contentType;
        document.sizeBytes = sizeBytes;
        document.visibility = visibility;
        document.status = DocumentStatus.PENDING;
        return document;
    }

    public void transitionTo(DocumentStatus newStatus) {
        this.status = newStatus;
    }

    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        this.failureReason = reason;
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

    public Organization getOrganization() {
        return organization;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public DocumentVisibility getVisibility() {
        return visibility;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
