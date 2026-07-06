package com.documind.guardrail.audit;

import com.documind.chat.domain.ChatMessage;
import com.documind.document.domain.Document;
import com.documind.guardrail.domain.FlaggedInteraction;
import com.documind.guardrail.domain.GuardrailSeverity;
import com.documind.guardrail.domain.GuardrailType;
import com.documind.guardrail.infrastructure.FlaggedInteractionRepository;
import com.documind.org.domain.Organization;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Records a guardrail violation so its effectiveness and any format-drift
 * over time can be monitored independently of the raw audit trail (see
 * AuditLogService). Every input/output guardrail in this package reports
 * through this single service.
 */
@Service
public class FlaggedInteractionService {

    private final FlaggedInteractionRepository flaggedInteractionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlaggedInteractionService(FlaggedInteractionRepository flaggedInteractionRepository) {
        this.flaggedInteractionRepository = flaggedInteractionRepository;
    }

    public void flag(
            Organization organization, ChatMessage chatMessage, Document document,
            GuardrailType guardrailType, GuardrailSeverity severity, Map<String, Object> details
    ) {
        FlaggedInteraction flaggedInteraction = FlaggedInteraction.flag(
                organization, chatMessage, document, guardrailType, severity, writeValueAsString(details)
        );
        flaggedInteractionRepository.save(flaggedInteraction);
    }

    private String writeValueAsString(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException("Failed to serialize flagged interaction details", exception);
        }
    }
}
