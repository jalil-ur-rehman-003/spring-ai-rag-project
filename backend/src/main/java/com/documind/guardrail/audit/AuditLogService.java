package com.documind.guardrail.audit;

import com.documind.auth.domain.User;
import com.documind.guardrail.domain.AuditLog;
import com.documind.guardrail.infrastructure.AuditLogRepository;
import com.documind.org.domain.Organization;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

/**
 * Records an append-only audit trail entry for every chat exchange and
 * privileged admin action. Kept independent of chat_message so the trail
 * survives even if chat history is ever pruned, and so it can capture
 * non-chat actions (uploads, role changes) with the same mechanism.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(
            Organization organization, User actorUser, String action, String resourceType, UUID resourceId,
            Map<String, Object> requestPayload, Map<String, Object> responseSummary, String ipAddress
    ) {
        AuditLog auditLog = AuditLog.record(
                organization, actorUser, action, resourceType, resourceId,
                writeValueAsString(requestPayload), writeValueAsString(responseSummary), ipAddress
        );
        auditLogRepository.save(auditLog);
    }

    private String writeValueAsString(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException("Failed to serialize audit log payload", exception);
        }
    }
}
