package com.documind.chat.web;

import com.documind.auth.web.AuthTokenResponse;
import com.documind.auth.web.LoginRequest;
import com.documind.auth.web.RegisterOrganizationRequest;
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ChatSessionControllerIntegrationTest {

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
        registry.add("documind.ingestion.scheduler.enabled", () -> "false");
        registry.add("documind.ai.voyage.api-key", () -> "not-needed-for-this-test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLoginReturningAccessToken() throws Exception {
        String email = "chat-user-" + System.nanoTime() + "@acme.test";
        RegisterOrganizationRequest registerRequest = new RegisterOrganizationRequest("Acme Corp", email, "SuperSecurePassword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        String loginResponseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "SuperSecurePassword123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(loginResponseBody, AuthTokenResponse.class).accessToken();
    }

    @Test
    void createsACollectionWideSessionWhenNoDocumentIdIsProvided() throws Exception {
        String accessToken = registerAndLoginReturningAccessToken();
        CreateChatSessionRequest request = new CreateChatSessionRequest(null, "General questions");

        mockMvc.perform(post("/api/v1/chat/sessions")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.documentId").doesNotExist())
                .andExpect(jsonPath("$.title").value("General questions"));
    }

    @Test
    void rejectsCreatingASessionForANonexistentDocument() throws Exception {
        String accessToken = registerAndLoginReturningAccessToken();
        CreateChatSessionRequest request = new CreateChatSessionRequest(java.util.UUID.randomUUID(), "title");

        mockMvc.perform(post("/api/v1/chat/sessions")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        CreateChatSessionRequest request = new CreateChatSessionRequest(null, "title");

        mockMvc.perform(post("/api/v1/chat/sessions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
