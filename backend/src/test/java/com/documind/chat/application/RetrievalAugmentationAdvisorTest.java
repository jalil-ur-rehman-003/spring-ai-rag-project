package com.documind.chat.application;

import com.documind.chat.domain.RetrievedChunk;
import com.documind.chat.infrastructure.DocumentChunkRetrievalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalAugmentationAdvisorTest {

    static final String ORGANIZATION_ID_CONTEXT_KEY = "documind.organizationId";
    static final String DOCUMENT_ID_CONTEXT_KEY = "documind.documentId";

    @Mock
    private DocumentChunkRetrievalRepository documentChunkRetrievalRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    private RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

    @BeforeEach
    void setUp() {
        retrievalAugmentationAdvisor = new RetrievalAugmentationAdvisor(
                documentChunkRetrievalRepository, embeddingModel, new RetrievalContextBuilder(), 5
        );
    }

    @Test
    void embedsTheQuestionAndAugmentsTheUserMessageWithRetrievedContext() {
        UUID organizationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        float[] queryEmbedding = {0.1f, 0.2f};

        when(embeddingModel.embed(eq("What is the vacation policy?"))).thenReturn(queryEmbedding);
        when(documentChunkRetrievalRepository.findMostSimilar(eq(organizationId), eq(documentId), eq(queryEmbedding), eq(5)))
                .thenReturn(List.of(new RetrievedChunk(UUID.randomUUID(), documentId, "20 days per year.", "Leave Policy", 3, 0.9)));

        ChatClientRequest originalRequest = ChatClientRequest.builder()
                .prompt(new Prompt("What is the vacation policy?"))
                .context(Map.of(ORGANIZATION_ID_CONTEXT_KEY, organizationId, DOCUMENT_ID_CONTEXT_KEY, documentId))
                .build();

        ChatClientRequest augmentedRequest = retrievalAugmentationAdvisor.before(originalRequest, null);

        String augmentedMessageText = augmentedRequest.prompt().getUserMessage().getText();
        assertThat(augmentedMessageText).contains("20 days per year.");
        assertThat(augmentedMessageText).contains("What is the vacation policy?");
    }

    @Test
    void supportsACollectionWideSessionWhereDocumentIdIsNull() {
        UUID organizationId = UUID.randomUUID();
        float[] queryEmbedding = {0.3f, 0.4f};

        when(embeddingModel.embed(eq("General question"))).thenReturn(queryEmbedding);
        when(documentChunkRetrievalRepository.findMostSimilar(eq(organizationId), eq(null), eq(queryEmbedding), eq(5)))
                .thenReturn(List.of());

        ChatClientRequest originalRequest = ChatClientRequest.builder()
                .prompt(new Prompt("General question"))
                .context(Map.of(ORGANIZATION_ID_CONTEXT_KEY, organizationId))
                .build();

        ChatClientRequest augmentedRequest = retrievalAugmentationAdvisor.before(originalRequest, null);

        assertThat(augmentedRequest.prompt().getUserMessage().getText()).containsIgnoringCase("no relevant");
    }
}
