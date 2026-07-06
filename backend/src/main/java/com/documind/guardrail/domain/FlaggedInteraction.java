package com.documind.guardrail.domain;

import com.documind.chat.domain.ChatMessage;
import com.documind.document.domain.Document;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One row per guardrail violation (input or output), so guardrail effectiveness and format-drift can be monitored independently of the raw audit trail. */
@Entity
@Table(name = "flagged_interaction")
public class FlaggedInteraction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id")
    private ChatMessage chatMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "guardrail_type", nullable = false)
    private GuardrailType guardrailType;

    @Enumerated(EnumType.STRING)
    private GuardrailSeverity severity;

    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    @Column(nullable = false)
    private boolean reviewed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FlaggedInteraction() {
        // required by JPA
    }

    public static FlaggedInteraction flag(
            Organization organization, ChatMessage chatMessage, Document document,
            GuardrailType guardrailType, GuardrailSeverity severity, String detailsJson
    ) {
        FlaggedInteraction flaggedInteraction = new FlaggedInteraction();
        flaggedInteraction.id = UUID.randomUUID();
        flaggedInteraction.organization = organization;
        flaggedInteraction.chatMessage = chatMessage;
        flaggedInteraction.document = document;
        flaggedInteraction.guardrailType = guardrailType;
        flaggedInteraction.severity = severity;
        flaggedInteraction.details = detailsJson;
        flaggedInteraction.reviewed = false;
        return flaggedInteraction;
    }

    public void markReviewed() {
        this.reviewed = true;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ChatMessage getChatMessage() {
        return chatMessage;
    }

    public Document getDocument() {
        return document;
    }

    public Organization getOrganization() {
        return organization;
    }

    public GuardrailType getGuardrailType() {
        return guardrailType;
    }

    public GuardrailSeverity getSeverity() {
        return severity;
    }

    public String getDetails() {
        return details;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
