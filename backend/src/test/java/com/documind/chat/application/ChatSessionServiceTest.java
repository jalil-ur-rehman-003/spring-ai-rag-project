package com.documind.chat.application;

import com.documind.auth.domain.User;
import com.documind.auth.domain.UserRole;
import com.documind.chat.domain.ChatSession;
import com.documind.chat.infrastructure.ChatSessionRepository;
import com.documind.common.error.EntityNotFoundException;
import com.documind.document.domain.Document;
import com.documind.document.domain.DocumentStatus;
import com.documind.document.domain.DocumentVisibility;
import com.documind.document.infrastructure.DocumentRepository;
import com.documind.org.domain.Organization;
import com.documind.org.domain.PlanTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private DocumentRepository documentRepository;

    private ChatSessionService chatSessionService;

    private Organization organization;
    private User user;

    @BeforeEach
    void setUp() {
        chatSessionService = new ChatSessionService(chatSessionRepository, documentRepository);
        organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
        user = User.createNew(organization, "admin@acme.test", "irrelevant-hash", UserRole.ADMIN);
    }

    @Test
    void createsACollectionWideSessionWhenNoDocumentIdIsGiven() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSession session = chatSessionService.createSession(user, organization, null, "General questions");

        assertThat(session.getDocument()).isNull();
        assertThat(session.getUser()).isEqualTo(user);
        assertThat(session.getOrganization()).isEqualTo(organization);
        assertThat(session.getTitle()).isEqualTo("General questions");
    }

    @Test
    void createsADocumentScopedSessionWhenTheDocumentBelongsToTheCallersOrganization() {
        Document document = Document.createPending(
                organization, user, "policy.pdf", "documents/policy.pdf", "application/pdf", 100, DocumentVisibility.ORG
        );
        document.transitionTo(DocumentStatus.READY);
        when(documentRepository.findById(any())).thenReturn(Optional.of(document));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSession session = chatSessionService.createSession(user, organization, document.getId(), "Policy Q&A");

        assertThat(session.getDocument()).isEqualTo(document);
    }

    @Test
    void rejectsCreatingASessionForADocumentThatBelongsToAnotherOrganization() {
        Organization otherOrganization = Organization.createNew("Other Org", PlanTier.FREE, 1024);
        User otherOrgUser = User.createNew(otherOrganization, "other@other.test", "irrelevant-hash", UserRole.ADMIN);
        Document documentFromAnotherOrg = Document.createPending(
                otherOrganization, otherOrgUser, "secret.pdf", "documents/secret.pdf", "application/pdf", 100, DocumentVisibility.ORG
        );
        when(documentRepository.findById(any())).thenReturn(Optional.of(documentFromAnotherOrg));

        assertThatThrownBy(() -> chatSessionService.createSession(user, organization, documentFromAnotherOrg.getId(), "title"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void rejectsCreatingASessionForANonexistentDocument() {
        when(documentRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatSessionService.createSession(user, organization, UUID.randomUUID(), "title"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
