package com.documind.guardrail.audit;

import com.documind.auth.domain.User;
import com.documind.auth.domain.UserRole;
import com.documind.guardrail.domain.AuditLog;
import com.documind.guardrail.infrastructure.AuditLogRepository;
import com.documind.org.domain.Organization;
import com.documind.org.domain.PlanTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;
    private Organization organization;
    private User user;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository);
        organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
        user = User.createNew(organization, "admin@acme.test", "irrelevant-hash", UserRole.ADMIN);
    }

    @Test
    void recordsAnAuditEntryWithSerializedRequestAndResponseSummaries() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditLogService.record(
                organization, user, "CHAT_QUERY", "ChatSession", user.getId(),
                Map.of("question", "What is the policy?"), Map.of("answerLength", 42), "127.0.0.1"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog savedEntry = captor.getValue();
        assertThat(savedEntry.getAction()).isEqualTo("CHAT_QUERY");
        assertThat(savedEntry.getOrganization()).isEqualTo(organization);
        assertThat(savedEntry.getActorUser()).isEqualTo(user);
        assertThat(savedEntry.getRequestPayload()).contains("What is the policy?");
        assertThat(savedEntry.getResponseSummary()).contains("42");
        assertThat(savedEntry.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void recordsAnEntryWithNoActorForSystemInitiatedActions() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditLogService.record(organization, null, "DOCUMENT_INGESTION_COMPLETED", "Document", null, Map.of(), Map.of(), null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorUser()).isNull();
    }
}
