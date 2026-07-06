package com.documind.chat.web;

import com.documind.auth.infrastructure.UserPrincipal;
import com.documind.chat.application.ChatSessionService;
import com.documind.chat.domain.ChatSession;
import com.documind.org.application.OrganizationService;
import com.documind.org.domain.Organization;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final OrganizationService organizationService;

    public ChatSessionController(ChatSessionService chatSessionService, OrganizationService organizationService) {
        this.chatSessionService = chatSessionService;
        this.organizationService = organizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse createSession(@Valid @RequestBody CreateChatSessionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Organization organization = organizationService.findByIdOrThrow(principal.getOrganizationId());
        ChatSession session = chatSessionService.createSession(principal.getUser(), organization, request.documentId(), request.title());

        return new ChatSessionResponse(
                session.getId(),
                session.getDocument() == null ? null : session.getDocument().getId(),
                session.getTitle()
        );
    }
}
