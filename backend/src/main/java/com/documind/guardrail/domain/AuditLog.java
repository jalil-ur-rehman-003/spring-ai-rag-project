package com.documind.guardrail.domain;

import com.documind.auth.domain.User;
import com.documind.org.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Append-only trail of every chat exchange and privileged admin action, independent of chat_message so it survives even if chat history is pruned. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_summary")
    private String responseSummary;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // required by JPA
    }

    public static AuditLog record(
            Organization organization, User actorUser, String action, String resourceType, UUID resourceId,
            String requestPayloadJson, String responseSummaryJson, String ipAddress
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.id = UUID.randomUUID();
        auditLog.organization = organization;
        auditLog.actorUser = actorUser;
        auditLog.action = action;
        auditLog.resourceType = resourceType;
        auditLog.resourceId = resourceId;
        auditLog.requestPayload = requestPayloadJson;
        auditLog.responseSummary = responseSummaryJson;
        auditLog.ipAddress = ipAddress;
        return auditLog;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public User getActorUser() {
        return actorUser;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public String getResponseSummary() {
        return responseSummary;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
