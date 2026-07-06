package com.documind.chat.web;

import com.documind.auth.infrastructure.UserPrincipal;
import com.documind.chat.application.ChatOrchestrationService;
import com.documind.chat.domain.ChatSession;
import com.documind.chat.infrastructure.ChatSessionRepository;
import com.documind.common.error.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/**
 * Streams the assistant's reply over SSE as it's generated, rather than
 * waiting for the full response -- matches the same SSE approach used for
 * ingestion progress (DocumentProgressController), keeping one streaming
 * pattern across the API instead of introducing WebSocket for this one case.
 */
@RestController
@RequestMapping("/api/v1/chat/sessions/{sessionId}/messages")
public class ChatController {

    private static final long NO_TIMEOUT = 0L;

    private final ChatOrchestrationService chatOrchestrationService;
    private final ChatSessionRepository chatSessionRepository;

    public ChatController(ChatOrchestrationService chatOrchestrationService, ChatSessionRepository chatSessionRepository) {
        this.chatOrchestrationService = chatOrchestrationService;
        this.chatSessionRepository = chatSessionRepository;
    }

    @PostMapping
    public SseEmitter streamAnswer(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AskQuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ChatSession session = findSessionScopedToOrganization(sessionId, principal.getOrganizationId());
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);

        UUID documentId = session.getDocument() == null ? null : session.getDocument().getId();

        chatOrchestrationService.streamAnswer(session, principal.getOrganizationId(), documentId, request.question())
                .subscribe(
                        chunk -> sendChunk(emitter, chunk),
                        emitter::completeWithError,
                        emitter::complete
                );

        return emitter;
    }

    private ChatSession findSessionScopedToOrganization(UUID sessionId, UUID organizationId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> EntityNotFoundException.forEntity("ChatSession", sessionId));

        if (!session.getOrganization().getId().equals(organizationId)) {
            throw EntityNotFoundException.forEntity("ChatSession", sessionId);
        }

        return session;
    }

    private void sendChunk(SseEmitter emitter, String chunk) {
        try {
            emitter.send(chunk);
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }
}
