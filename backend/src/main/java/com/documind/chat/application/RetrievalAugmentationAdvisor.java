package com.documind.chat.application;

import com.documind.chat.domain.RetrievedChunk;
import com.documind.chat.infrastructure.DocumentChunkRetrievalRepository;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.UUID;

/**
 * Hand-rolled equivalent of Spring AI's built-in QuestionAnswerAdvisor,
 * calling DocumentChunkRetrievalRepository directly instead of the generic
 * VectorStore API -- see docs/DECISIONS.md for why: we bypass VectorStore
 * everywhere for document_chunk to keep first-class organization_id/
 * document_id columns, and QuestionAnswerAdvisor is hard-coupled to
 * VectorStore/SearchRequest.
 *
 * Reads organizationId (required) and documentId (optional -- null means a
 * collection-wide session) from the ChatClientRequest's context map, which
 * the caller (ChatOrchestrationService) populates per-request from the
 * authenticated principal and the chat session, so retrieval is always
 * tenant-scoped and never trusts anything from the prompt/user input itself.
 */
public class RetrievalAugmentationAdvisor implements BaseAdvisor {

    public static final String ORGANIZATION_ID_CONTEXT_KEY = "documind.organizationId";
    public static final String DOCUMENT_ID_CONTEXT_KEY = "documind.documentId";
    public static final String RETRIEVED_CHUNKS_CONTEXT_KEY = "documind.retrievedChunks";

    private final DocumentChunkRetrievalRepository documentChunkRetrievalRepository;
    private final EmbeddingModel embeddingModel;
    private final RetrievalContextBuilder retrievalContextBuilder;
    private final int topK;

    public RetrievalAugmentationAdvisor(
            DocumentChunkRetrievalRepository documentChunkRetrievalRepository,
            EmbeddingModel embeddingModel,
            RetrievalContextBuilder retrievalContextBuilder,
            int topK
    ) {
        this.documentChunkRetrievalRepository = documentChunkRetrievalRepository;
        this.embeddingModel = embeddingModel;
        this.retrievalContextBuilder = retrievalContextBuilder;
        this.topK = topK;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        UUID organizationId = (UUID) request.context().get(ORGANIZATION_ID_CONTEXT_KEY);
        UUID documentId = (UUID) request.context().get(DOCUMENT_ID_CONTEXT_KEY);
        String userQuestion = request.prompt().getUserMessage().getText();

        float[] queryEmbedding = embeddingModel.embed(userQuestion);
        List<RetrievedChunk> retrievedChunks = documentChunkRetrievalRepository.findMostSimilar(
                organizationId, documentId, queryEmbedding, topK
        );

        String augmentedMessageText = retrievalContextBuilder.buildAugmentedUserMessage(userQuestion, retrievedChunks);
        Prompt augmentedPrompt = new Prompt(new UserMessage(augmentedMessageText));

        return request.mutate()
                .prompt(augmentedPrompt)
                .context(RETRIEVED_CHUNKS_CONTEXT_KEY, retrievedChunks)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
