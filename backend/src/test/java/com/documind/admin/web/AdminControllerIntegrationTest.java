package com.documind.admin.web;

import com.documind.auth.web.AuthTokenResponse;
import com.documind.auth.web.LoginRequest;
import com.documind.auth.web.RegisterOrganizationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AdminControllerIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record RegisteredAdmin(String accessToken, UUID organizationId, UUID userId) {
    }

    private RegisteredAdmin registerAndLoginAsAdmin() throws Exception {
        String email = "admin-test-" + System.nanoTime() + "@acme.test";
        RegisterOrganizationRequest registerRequest = new RegisterOrganizationRequest("Acme Corp", email, "SuperSecurePassword123");

        String registerResponseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var registerResponse = objectMapper.readTree(registerResponseBody);
        UUID organizationId = UUID.fromString(registerResponse.get("organizationId").asText());
        UUID userId = UUID.fromString(registerResponse.get("adminUserId").asText());

        String loginResponseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "SuperSecurePassword123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readValue(loginResponseBody, AuthTokenResponse.class).accessToken();
        return new RegisteredAdmin(accessToken, organizationId, userId);
    }

    @Test
    void adminCanViewUsageForTheirOwnOrganization() throws Exception {
        RegisteredAdmin admin = registerAndLoginAsAdmin();

        mockMvc.perform(get("/api/v1/admin/usage").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(0))
                .andExpect(jsonPath("$.storageQuotaBytes").isNumber());
    }

    @Test
    void adminCanListUsersInTheirOwnOrganization() throws Exception {
        RegisteredAdmin admin = registerAndLoginAsAdmin();

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(admin.userId().toString()))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void adminCanDisableAUserAndTheChangePersistsToTheDatabase() throws Exception {
        RegisteredAdmin admin = registerAndLoginAsAdmin();
        UUID memberUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_user (id, organization_id, email, password_hash, role) VALUES (?, ?, ?, 'hash', 'VIEWER')",
                memberUserId, admin.organizationId(), "member-" + memberUserId + "@acme.test"
        );

        mockMvc.perform(post("/api/v1/admin/users/{userId}/disable", memberUserId)
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNoContent());

        String status = jdbcTemplate.queryForObject("SELECT status FROM app_user WHERE id = ?", String.class, memberUserId);
        assertThat(status).isEqualTo("DISABLED");
    }

    @Test
    void aUserDemotedToViewerIsImmediatelyForbiddenFromAdminEndpointsOnTheirNextRequest() throws Exception {
        RegisteredAdmin admin = registerAndLoginAsAdmin();

        // JwtAuthenticationFilter reloads the User entity fresh from the database on every request
        // (a deliberate Phase 1 tradeoff -- see docs/DECISIONS.md -- so a role change or account
        // disable takes effect immediately, not only after the short-lived access token expires).
        // This confirms that behavior end-to-end: demote the admin to VIEWER via the endpoint under
        // test, then reuse their *same, already-issued* access token and confirm it's now rejected,
        // without needing to mint a fresh VIEWER-role token from scratch.
        mockMvc.perform(post("/api/v1/admin/users/{userId}/role", admin.userId())
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeUserRoleRequest(com.documind.auth.domain.UserRole.VIEWER))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/usage").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreForbiddenFromAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/usage"))
                .andExpect(status().isForbidden());
    }
}
