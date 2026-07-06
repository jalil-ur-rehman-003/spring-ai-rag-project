package com.documind.chat.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * citations/guardrailFlags are stored as raw JSON strings (mapped via
 * @JdbcTypeCode(SqlTypes.JSON) to the column's JSONB type) rather than a
 * structured Java type -- their shape is still being finalized as the
 * guardrail suite (Phase 4) is built, and a plain JSON string avoids
 * churning this entity's mapping every time that shape changes.
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessageRole role;

    @Column(nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    private String citations;

    @Column(name = "groundedness_score")
    private BigDecimal groundednessScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "guardrail_flags")
    private String guardrailFlags;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
        // required by JPA
    }

    public static ChatMessage createNew(ChatSession session, ChatMessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID();
        message.session = session;
        message.role = role;
        message.content = content;
        return message;
    }

    public void attachCitations(String citationsJson) {
        this.citations = citationsJson;
    }

    public void attachGuardrailFlags(String guardrailFlagsJson) {
        this.guardrailFlags = guardrailFlagsJson;
    }

    public void recordGroundednessScore(BigDecimal score) {
        this.groundednessScore = score;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ChatSession getSession() {
        return session;
    }

    public ChatMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getCitations() {
        return citations;
    }

    public BigDecimal getGroundednessScore() {
        return groundednessScore;
    }

    public String getGuardrailFlags() {
        return guardrailFlags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
