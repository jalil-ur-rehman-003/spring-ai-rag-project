package com.documind.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full auth flow against a real, throwaway Postgres+pgvector
 * container -- this is the test class that would have caught both the
 * citext/Hibernate schema mismatch and the LazyInitializationException on
 * /auth/refresh found during manual verification, since a mocked repository
 * cannot reproduce a real Hibernate session boundary or Flyway-applied schema.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void registerJwtSigningKey(DynamicPropertyRegistry registry) {
        // Excludes the pgvector VectorStore auto-configuration for the same reason application.yml
        // does in the running app: no EmbeddingModel bean exists until Phase 2's Voyage adapter is built.
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
        );
        registry.add("documind.security.jwt.signing-key", () -> "integration-test-signing-key-256-bits-minimum-length");
        registry.add("spring.ai.anthropic.api-key", () -> "not-needed-for-auth-tests");

        // The S3Client bean (ObjectStorageConfig) is constructed eagerly regardless of whether this
        // test touches document upload -- it just needs non-blank credentials to build without error.
        registry.add("documind.object-storage.secret-key", () -> "test-secret-key-not-actually-used");
        registry.add("documind.ingestion.scheduler.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullAuthLifecycleSucceeds() throws Exception {
        String uniqueEmail = "integration-" + System.nanoTime() + "@acme.test";

        RegisterOrganizationRequest registerRequest =
                new RegisterOrganizationRequest("Integration Test Org", uniqueEmail, "SuperSecurePassword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").exists())
                .andExpect(jsonPath("$.adminUserId").exists());

        LoginRequest loginRequest = new LoginRequest(uniqueEmail, "SuperSecurePassword123");

        String loginResponseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();

        AuthTokenResponse initialTokens = objectMapper.readValue(loginResponseBody, AuthTokenResponse.class);

        // This is the exact call that previously 500'd with a LazyInitializationException
        // before AuthenticationService.refresh() was made @Transactional.
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(initialTokens.refreshToken());

        String refreshResponseBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();

        AuthTokenResponse rotatedTokens = objectMapper.readValue(refreshResponseBody, AuthTokenResponse.class);

        RefreshTokenRequest logoutRequest = new RefreshTokenRequest(rotatedTokens.refreshToken());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());

        // The revoked (just-logged-out) refresh token must no longer work.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isUnauthorized());

        // The original pre-rotation refresh token must also no longer work.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(initialTokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registeringWithADuplicateEmailIsRejected() throws Exception {
        String duplicateEmail = "duplicate-" + System.nanoTime() + "@acme.test";
        RegisterOrganizationRequest firstRegistration =
                new RegisterOrganizationRequest("First Org", duplicateEmail, "SuperSecurePassword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(firstRegistration)))
                .andExpect(status().isCreated());

        RegisterOrganizationRequest secondRegistrationSameEmail =
                new RegisterOrganizationRequest("Second Org", duplicateEmail, "AnotherSecurePassword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(secondRegistrationSameEmail)))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        String email = "wrong-password-" + System.nanoTime() + "@acme.test";
        RegisterOrganizationRequest registerRequest = new RegisterOrganizationRequest("Org", email, "CorrectPassword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest wrongPasswordLogin = new LoginRequest(email, "WrongPassword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(wrongPasswordLogin)))
                .andExpect(status().isUnauthorized());
    }
}
