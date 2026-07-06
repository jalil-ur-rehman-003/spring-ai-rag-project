package com.documind.guardrail.output;

import com.documind.chat.domain.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroundednessJudgeTest {

    @Mock
    private ChatModel chatModel;

    private GroundednessJudge groundednessJudge;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        groundednessJudge = new GroundednessJudge(chatClient, new FormatValidationAdvisor());
    }

    private void stubModelResponse(String responseText) {
        AssistantMessage assistantMessage = new AssistantMessage(responseText);
        Generation generation = new Generation(assistantMessage, ChatGenerationMetadata.NULL);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(generation)));
    }

    @Test
    void returnsAGroundedVerdictWhenTheJudgeConfirmsSupport() {
        stubModelResponse("{\"grounded\": true, \"score\": 0.95, \"reasoning\": \"The answer matches the source text.\"}");

        GroundednessVerdict verdict = groundednessJudge.evaluate(
                "Vacation is 20 days per year.",
                List.of(new RetrievedChunk(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Vacation policy: 20 days per year.", null, null, 0.9))
        );

        assertThat(verdict.grounded()).isTrue();
        assertThat(verdict.score()).isEqualTo(0.95);
    }

    @Test
    void returnsAnUngroundedVerdictWhenTheJudgeDisagrees() {
        stubModelResponse("{\"grounded\": false, \"score\": 0.1, \"reasoning\": \"The answer isn't supported by the context.\"}");

        GroundednessVerdict verdict = groundednessJudge.evaluate("Vacation is unlimited.", List.of());

        assertThat(verdict.grounded()).isFalse();
    }

    @Test
    void failsClosedAsUngroundedWhenTheJudgesResponseIsUnparseableEvenAfterRetry() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("not JSON at all"), ChatGenerationMetadata.NULL)))
        );

        GroundednessVerdict verdict = groundednessJudge.evaluate("Some answer.", List.of());

        assertThat(verdict.grounded()).isFalse();
    }
}
