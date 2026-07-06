package com.documind.chat.application;

import com.documind.auth.domain.User;
import com.documind.chat.domain.ChatSession;
import com.documind.chat.infrastructure.ChatSessionRepository;
import com.documind.common.error.EntityNotFoundException;
import com.documind.document.domain.Document;
import com.documind.document.infrastructure.DocumentRepository;
import com.documind.org.domain.Organization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final DocumentRepository documentRepository;

    public ChatSessionService(ChatSessionRepository chatSessionRepository, DocumentRepository documentRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.documentRepository = documentRepository;
    }

    /** documentId null creates a collection-wide session (retrieval spans every READY document in the org); non-null scopes retrieval to that one document. */
    @Transactional
    public ChatSession createSession(User user, Organization organization, UUID documentId, String title) {
        Document document = documentId == null ? null : findDocumentScopedToOrganization(documentId, organization);
        ChatSession session = ChatSession.createNew(user, organization, document, title);
        return chatSessionRepository.save(session);
    }

    private Document findDocumentScopedToOrganization(UUID documentId, Organization organization) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> EntityNotFoundException.forEntity("Document", documentId));

        if (!document.getOrganization().getId().equals(organization.getId())) {
            // Same 404 as "not found" rather than 403 -- doesn't reveal that a document with this id
            // exists in a different organization at all.
            throw EntityNotFoundException.forEntity("Document", documentId);
        }

        return document;
    }
}
