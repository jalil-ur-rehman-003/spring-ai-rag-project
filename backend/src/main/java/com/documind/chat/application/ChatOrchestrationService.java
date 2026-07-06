package com.documind.chat.application;

import com.documind.chat.domain.ChatMessage;
import com.documind.chat.domain.ChatMessageRole;
import com.documind.chat.domain.ChatSession;
import com.documind.chat.domain.RetrievedChunk;
import com.documind.chat.infrastructure.ChatMessageRepository;
import com.documind.guardrail.audit.AuditLogService;
import com.documind.guardrail.audit.FlaggedInteractionService;
import com.documind.guardrail.domain.GuardrailSeverity;
import com.documind.guardrail.domain.GuardrailType;
import com.documind.guardrail.input.PromptInjectionGuard;
import com.documind.guardrail.input.RateLimitGuard;
import com.documind.guardrail.output.CitationEnforcer;
import com.documind.guardrail.output.GroundednessJudge;
import com.documind.guardrail.output.GroundednessVerdict;
import com.documind.guardrail.output.ScopeRefusalGuard;
import com.documind.guardrail.output.ToxicityFilter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Orchestrates a single chat turn end to end: input guardrails (cheapest
 * first, short-circuit on failure, before any model call) -> persist the
 * user's question -> ask Claude (via ChatClient, with
 * RetrievalAugmentationAdvisor injecting retrieved context per-call) ->
 * output guardrails (citation presence -> scope refusal -> toxicity ->
 * groundedness LLM-judge last, since it's the most expensive check) ->
 * persist the assistant's reply -> audit log every exchange.
 *
 * For the streaming path, output guardrails can only run once the full
 * answer has been assembled -- by then the chunks have already reached the
 * browser, so a violation there is flagged for audit/review rather than
 * blocking already-streamed content (a deliberate, confirmed tradeoff; see
 * docs/DECISIONS.md).
 */
@Service
public class ChatOrchestrationService {

    private static final String REFUSAL_ON_GUARDRAIL_FAILURE =
            "I couldn't verify that answer against the source material, so I'm not able to share it. Please try rephrasing your question.";

    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final PromptInjectionGuard promptInjectionGuard;
    private final RateLimitGuard rateLimitGuard;
    private final CitationEnforcer citationEnforcer;
    private final ScopeRefusalGuard scopeRefusalGuard;
    private final ToxicityFilter toxicityFilter;
    private final GroundednessJudge groundednessJudge;
    private final AuditLogService auditLogService;
    private final FlaggedInteractionService flaggedInteractionService;

    public ChatOrchestrationService(
            ChatClient chatClient,
            ChatMessageRepository chatMessageRepository,
            PromptInjectionGuard promptInjectionGuard,
            RateLimitGuard rateLimitGuard,
            CitationEnforcer citationEnforcer,
            ScopeRefusalGuard scopeRefusalGuard,
            ToxicityFilter toxicityFilter,
            GroundednessJudge groundednessJudge,
            AuditLogService auditLogService,
            FlaggedInteractionService flaggedInteractionService
    ) {
        this.chatClient = chatClient;
        this.chatMessageRepository = chatMessageRepository;
        this.promptInjectionGuard = promptInjectionGuard;
        this.rateLimitGuard = rateLimitGuard;
        this.citationEnforcer = citationEnforcer;
        this.scopeRefusalGuard = scopeRefusalGuard;
        this.toxicityFilter = toxicityFilter;
        this.groundednessJudge = groundednessJudge;
        this.auditLogService = auditLogService;
        this.flaggedInteractionService = flaggedInteractionService;
    }

    @Transactional
    public String askQuestion(ChatSession session, UUID organizationId, UUID documentId, String userQuestion) {
        runInputGuardrails(session, userQuestion);

        persistMessage(session, ChatMessageRole.USER, userQuestion);

        ChatClientResponse response = chatClient.prompt()
                .user(userQuestion)
                .advisors(retrievalScopeParams(organizationId, documentId))
                .call()
                .chatClientResponse();

        String rawAnswer = response.chatResponse().getResult().getOutput().getText();
        List<RetrievedChunk> retrievedChunks = retrievedChunksFrom(response);

        String finalAnswer = runOutputGuardrails(session, rawAnswer, retrievedChunks);

        persistMessage(session, ChatMessageRole.ASSISTANT, finalAnswer);
        auditLogService.record(
                session.getOrganization(), session.getUser(), "CHAT_QUERY", "ChatSession", session.getId(),
                Map.of("question", userQuestion), Map.of("answerLength", finalAnswer.length()), null
        );

        return finalAnswer;
    }

    /**
     * Streams the assistant's reply chunk-by-chunk for SSE, accumulating the
     * full text so output guardrails can still evaluate it once the stream
     * completes -- but by then the chunks are already with the client, so a
     * violation here is flagged for audit/review rather than blocking
     * content that's already been shown.
     */
    public Flux<String> streamAnswer(ChatSession session, UUID organizationId, UUID documentId, String userQuestion) {
        runInputGuardrails(session, userQuestion);

        persistMessage(session, ChatMessageRole.USER, userQuestion);

        StringBuilder accumulatedAnswer = new StringBuilder();

        return chatClient.prompt()
                .user(userQuestion)
                .advisors(retrievalScopeParams(organizationId, documentId))
                .stream()
                .content()
                .doOnNext(accumulatedAnswer::append)
                .doOnComplete(() -> {
                    String fullAnswer = accumulatedAnswer.toString();
                    persistMessage(session, ChatMessageRole.ASSISTANT, fullAnswer);
                    flagOutputGuardrailViolationsWithoutBlocking(session, fullAnswer);
                    auditLogService.record(
                            session.getOrganization(), session.getUser(), "CHAT_QUERY", "ChatSession", session.getId(),
                            Map.of("question", userQuestion), Map.of("answerLength", fullAnswer.length()), null
                    );
                });
    }

    private void runInputGuardrails(ChatSession session, String userQuestion) {
        rateLimitGuard.checkAndConsume(session.getUser().getId());
        promptInjectionGuard.check(userQuestion);
    }

    /** Synchronous path: a guardrail failure replaces the answer with a refusal message rather than returning the unverified content. */
    private String runOutputGuardrails(ChatSession session, String rawAnswer, List<RetrievedChunk> retrievedChunks) {
        if (scopeRefusalGuard.isOutOfScope(retrievedChunks)) {
            flaggedInteractionService.flag(
                    session.getOrganization(), null, session.getDocument(),
                    GuardrailType.OUT_OF_SCOPE, GuardrailSeverity.LOW, Map.of()
            );
            return scopeRefusalGuard.buildRefusalMessage();
        }

        if (!citationEnforcer.hasValidCitations(rawAnswer, retrievedChunks)) {
            flaggedInteractionService.flag(
                    session.getOrganization(), null, session.getDocument(),
                    GuardrailType.FORMAT_VIOLATION, GuardrailSeverity.MEDIUM, Map.of("reason", "missing or invalid citations")
            );
            return REFUSAL_ON_GUARDRAIL_FAILURE;
        }

        if (toxicityFilter.isToxic(rawAnswer)) {
            flaggedInteractionService.flag(
                    session.getOrganization(), null, session.getDocument(),
                    GuardrailType.TOXICITY, GuardrailSeverity.HIGH, Map.of()
            );
            return REFUSAL_ON_GUARDRAIL_FAILURE;
        }

        GroundednessVerdict groundednessVerdict = groundednessJudge.evaluate(rawAnswer, retrievedChunks);
        if (!groundednessVerdict.grounded()) {
            flaggedInteractionService.flag(
                    session.getOrganization(), null, session.getDocument(),
                    GuardrailType.LOW_GROUNDEDNESS, GuardrailSeverity.MEDIUM, Map.of("reasoning", groundednessVerdict.reasoning())
            );
            return REFUSAL_ON_GUARDRAIL_FAILURE;
        }

        return citationEnforcer.stripCitationMarkers(rawAnswer);
    }

    /** Streaming path: flags violations for audit/review without attempting to alter content that's already reached the client. */
    private void flagOutputGuardrailViolationsWithoutBlocking(ChatSession session, String fullAnswer) {
        if (toxicityFilter.isToxic(fullAnswer)) {
            flaggedInteractionService.flag(
                    session.getOrganization(), null, session.getDocument(),
                    GuardrailType.TOXICITY, GuardrailSeverity.HIGH, Map.of("streamed", true)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<RetrievedChunk> retrievedChunksFrom(ChatClientResponse response) {
        Object retrievedChunks = response.context().get(RetrievalAugmentationAdvisor.RETRIEVED_CHUNKS_CONTEXT_KEY);
        return retrievedChunks == null ? List.of() : (List<RetrievedChunk>) retrievedChunks;
    }

    private Consumer<ChatClient.AdvisorSpec> retrievalScopeParams(UUID organizationId, UUID documentId) {
        return advisorSpec -> {
            advisorSpec.param(RetrievalAugmentationAdvisor.ORGANIZATION_ID_CONTEXT_KEY, organizationId);
            // AdvisorSpec.param() rejects a null value outright, but documentId is legitimately
            // null for a collection-wide session -- only set the key when there's an actual
            // document to scope to; RetrievalAugmentationAdvisor treats a missing key the same as
            // an explicit null (both mean "search every document").
            if (documentId != null) {
                advisorSpec.param(RetrievalAugmentationAdvisor.DOCUMENT_ID_CONTEXT_KEY, documentId);
            }
        };
    }

    private void persistMessage(ChatSession session, ChatMessageRole role, String content) {
        chatMessageRepository.save(ChatMessage.createNew(session, role, content));
    }
}
