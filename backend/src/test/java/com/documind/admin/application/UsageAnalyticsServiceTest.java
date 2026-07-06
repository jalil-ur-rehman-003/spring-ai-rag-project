package com.documind.admin.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full @SpringBootTest with real Postgres: UsageAnalyticsService's queries
 * span document/chat_session/chat_message/organization, which a mocked
 * repository can't meaningfully verify (aggregate counts, joins, and
 * cross-organization isolation are exactly the kind of thing that needs a
 * real database).
 */
@Testcontainers
@SpringBootTest
class UsageAnalyticsServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
        );
        registry.add("documind.security.jwt.signing-key", () -> "integration-test-signing-key-256-bits-minimum-length");
        registry.add("spring.ai.anthropic.api-key", () -> "not-needed-for-this-test");
        registry.add("documind.object-storage.secret-key", () -> "test-secret-key-not-actually-used");
        registry.add("documind.ai.voyage.api-key", () -> "not-needed-for-this-test");
        registry.add("documind.ingestion.scheduler.enabled", () -> "false");
    }

    @Autowired
    private UsageAnalyticsService usageAnalyticsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID organizationId;
    private UUID userId;

    @BeforeEach
    void setUpOrganizationAndUser() {
        organizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organization (id, name, plan_tier, storage_quota_bytes, storage_used_bytes) VALUES (?, ?, 'FREE', 1000, 250)",
                organizationId, "Test Org " + organizationId
        );

        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_user (id, organization_id, email, password_hash, role) VALUES (?, ?, ?, 'hash', 'ADMIN')",
                userId, organizationId, "analytics-test-" + userId + "@acme.test"
        );
    }

    private UUID insertDocument(String status) {
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO document (id, organization_id, uploaded_by, original_filename, storage_key, size_bytes, visibility, status)
                VALUES (?, ?, ?, 'test.pdf', 'documents/test.pdf', 100, 'ORG', ?)
                """,
                documentId, organizationId, userId, status
        );
        return documentId;
    }

    private UUID insertChatSession() {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO chat_session (id, user_id, organization_id, title) VALUES (?, ?, ?, 'Test session')",
                sessionId, userId, organizationId
        );
        return sessionId;
    }

    private void insertChatMessage(UUID sessionId, String role) {
        jdbcTemplate.update(
                "INSERT INTO chat_message (id, session_id, role, content) VALUES (?, ?, ?, 'test content')",
                UUID.randomUUID(), sessionId, role
        );
    }

    @Test
    void summarizesDocumentCountsByStatus() {
        insertDocument("READY");
        insertDocument("READY");
        insertDocument("FAILED");
        insertDocument("PENDING");

        OrganizationUsageSummary summary = usageAnalyticsService.summarizeUsage(organizationId);

        assertThat(summary.totalDocuments()).isEqualTo(4);
        assertThat(summary.readyDocuments()).isEqualTo(2);
        assertThat(summary.failedDocuments()).isEqualTo(1);
    }

    @Test
    void summarizesChatSessionAndMessageVolume() {
        UUID firstSession = insertChatSession();
        UUID secondSession = insertChatSession();
        insertChatMessage(firstSession, "USER");
        insertChatMessage(firstSession, "ASSISTANT");
        insertChatMessage(secondSession, "USER");

        OrganizationUsageSummary summary = usageAnalyticsService.summarizeUsage(organizationId);

        assertThat(summary.totalChatSessions()).isEqualTo(2);
        assertThat(summary.totalChatMessages()).isEqualTo(3);
    }

    @Test
    void includesStorageUsageAndQuotaFromTheOrganizationRow() {
        OrganizationUsageSummary summary = usageAnalyticsService.summarizeUsage(organizationId);

        assertThat(summary.storageUsedBytes()).isEqualTo(250);
        assertThat(summary.storageQuotaBytes()).isEqualTo(1000);
    }

    @Test
    void neverCountsDocumentsOrSessionsFromAnotherOrganization() {
        UUID otherOrganizationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organization (id, name, plan_tier) VALUES (?, ?, 'FREE')", otherOrganizationId, "Other Org");
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_user (id, organization_id, email, password_hash, role) VALUES (?, ?, ?, 'hash', 'ADMIN')",
                otherUserId, otherOrganizationId, "other-org-" + otherUserId + "@acme.test"
        );
        jdbcTemplate.update(
                """
                INSERT INTO document (id, organization_id, uploaded_by, original_filename, storage_key, size_bytes, visibility, status)
                VALUES (?, ?, ?, 'other.pdf', 'documents/other.pdf', 100, 'ORG', 'READY')
                """,
                UUID.randomUUID(), otherOrganizationId, otherUserId
        );

        insertDocument("READY");

        OrganizationUsageSummary summary = usageAnalyticsService.summarizeUsage(organizationId);

        assertThat(summary.totalDocuments()).isEqualTo(1);
    }
}
