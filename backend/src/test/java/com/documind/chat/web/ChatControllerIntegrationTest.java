package com.documind.chat.web;

import com.documind.auth.web.AuthTokenResponse;
import com.documind.auth.web.LoginRequest;
import com.documind.auth.web.RegisterOrganizationRequest;
import com.documind.chat.web.CreateChatSessionRequest;
import com.documind.chat.web.ChatSessionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses a stubbed ChatModel bean (@TestConfiguration, @Primary) instead of a
 * real Anthropic call, since no live ANTHROPIC_API_KEY is available in this
 * environment -- confirms the SSE wiring, session scoping, and message
 * persistence around the model call, not the live Claude integration itself.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ChatControllerIntegrationTest {

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

    @TestConfiguration
    static class StubChatModelConfig {
        @Bean
        @Primary
        ChatModel stubChatModel() {
            ChatModel stub = mock(ChatModel.class);
            AssistantMessage assistantMessage = new AssistantMessage("The vacation policy allows 20 days per year.");
            Generation generation = new Generation(assistantMessage, ChatGenerationMetadata.NULL);
            when(stub.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(generation))));
            return stub;
        }

        // Without this, RetrievalAugmentationAdvisor's real VoyageEmbeddingModel bean would attempt
        // a genuine HTTP call to api.voyageai.com with a fake test API key before the ChatModel is
        // ever reached -- the resulting failure was silently swallowed by the reactive chain and
        // surfaced only as an empty SSE response body, not an obvious test failure message.
        @Bean
        @Primary
        EmbeddingModel stubEmbeddingModel() {
            EmbeddingModel stub = mock(EmbeddingModel.class);
            when(stub.embed(any(String.class))).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
            return stub;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLoginReturningAccessToken() throws Exception {
        String email = "chat-msg-" + System.nanoTime() + "@acme.test";
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

    private String createSessionReturningId(String accessToken) throws Exception {
        String sessionResponseBody = mockMvc.perform(post("/api/v1/chat/sessions")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(new CreateChatSessionRequest(null, "Test session"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(sessionResponseBody, ChatSessionResponse.class).sessionId().toString();
    }

    @Test
    void streamsTheAssistantsReplyOverSse() throws Exception {
        String accessToken = registerAndLoginReturningAccessToken();
        String sessionId = createSessionReturningId(accessToken);

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(new AskQuestionRequest("What is the vacation policy?"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        // SseEmitter streams by writing directly to the response as the underlying Flux emits --
        // like the ingestion-progress SSE endpoint, it never sets Spring MVC's async "result" value,
        // so getAsyncResult() can't be used to wait for it. The mocked ChatModel's Flux completes on
        // Reactor's default scheduler, genuinely asynchronously with respect to this thread (unlike
        // the earlier progress-endpoint test, where publish() ran synchronously on the caller's
        // thread), so this needs an actual wait for the response body to be written.
        String responseBody = awaitNonEmptyResponseBody(mvcResult, java.time.Duration.ofSeconds(5));
        org.assertj.core.api.Assertions.assertThat(responseBody).contains("The vacation policy allows 20 days per year.");
    }

    private String awaitNonEmptyResponseBody(MvcResult mvcResult, java.time.Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            String body = mvcResult.getResponse().getContentAsString();
            if (!body.isEmpty()) {
                return body;
            }
            Thread.sleep(50);
        }
        return mvcResult.getResponse().getContentAsString();
    }

    @Test
    void rejectsStreamingForASessionBelongingToAnotherOrganization() throws Exception {
        String firstUserToken = registerAndLoginReturningAccessToken();
        String sessionIdFromFirstOrg = createSessionReturningId(firstUserToken);

        String secondUserToken = registerAndLoginReturningAccessToken();

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionIdFromFirstOrg)
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + secondUserToken)
                        .content(objectMapper.writeValueAsString(new AskQuestionRequest("question"))))
                .andExpect(status().isNotFound());
    }
}
