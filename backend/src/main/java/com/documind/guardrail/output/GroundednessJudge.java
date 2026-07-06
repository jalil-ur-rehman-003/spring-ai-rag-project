package com.documind.guardrail.output;

import com.documind.chat.domain.RetrievedChunk;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM-as-judge groundedness check: asks the model whether an answer is
 * actually supported by the retrieved context, via a structured JSON
 * verdict parsed through FormatValidationAdvisor's fallback chain. Run
 * last in the output guardrail chain since it's the most expensive check
 * (a full secondary model call) -- only worth paying for once an answer
 * has already passed the cheaper checks (citation presence, toxicity).
 *
 * The corrective retry (invoked by FormatValidationAdvisor on a parse
 * failure) reuses the same ChatClient with a corrective prompt rather than
 * a second, different model -- see FormatValidationAdvisor's contract.
 */
@Component
public class GroundednessJudge {

    private static final String JUDGE_PROMPT_TEMPLATE = """
            You are evaluating whether an AI-generated answer is supported by the provided source context.

            Source context:
            %s

            Answer to evaluate:
            %s

            Respond with only a JSON object matching this schema, no surrounding prose:
            {"grounded": boolean, "score": number between 0 and 1, "reasoning": "brief explanation"}
            """;

    private final ChatClient chatClient;
    private final FormatValidationAdvisor formatValidationAdvisor;

    public GroundednessJudge(ChatClient chatClient, FormatValidationAdvisor formatValidationAdvisor) {
        this.chatClient = chatClient;
        this.formatValidationAdvisor = formatValidationAdvisor;
    }

    public GroundednessVerdict evaluate(String answer, List<RetrievedChunk> retrievedChunks) {
        String context = retrievedChunks.isEmpty()
                ? "(no context was retrieved for this question)"
                : retrievedChunks.stream().map(RetrievedChunk::content).reduce("", (a, b) -> a + "\n\n" + b);

        String judgePrompt = JUDGE_PROMPT_TEMPLATE.formatted(context, answer);
        String rawResponse = callJudgeModel(judgePrompt);

        return formatValidationAdvisor
                .parseWithFallback(rawResponse, GroundednessVerdict.class, this::callJudgeModel)
                .orElseGet(this::failClosedVerdict);
    }

    private String callJudgeModel(String prompt) {
        return chatClient.prompt().user(prompt).call().content();
    }

    private GroundednessVerdict failClosedVerdict() {
        // Fail closed: an unparseable judge response is treated at least as cautiously as an
        // explicit "not grounded" verdict, never assumed grounded.
        return new GroundednessVerdict(false, 0.0, "Groundedness could not be determined (unparseable judge response).");
    }
}
