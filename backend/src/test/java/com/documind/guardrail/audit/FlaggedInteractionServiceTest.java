package com.documind.guardrail.audit;

import com.documind.guardrail.domain.FlaggedInteraction;
import com.documind.guardrail.domain.GuardrailSeverity;
import com.documind.guardrail.domain.GuardrailType;
import com.documind.guardrail.infrastructure.FlaggedInteractionRepository;
import com.documind.org.domain.Organization;
import com.documind.org.domain.PlanTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlaggedInteractionServiceTest {

    @Mock
    private FlaggedInteractionRepository flaggedInteractionRepository;

    private FlaggedInteractionService flaggedInteractionService;
    private Organization organization;

    @BeforeEach
    void setUp() {
        flaggedInteractionService = new FlaggedInteractionService(flaggedInteractionRepository);
        organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
    }

    @Test
    void flagsAnInteractionWithSerializedDetails() {
        when(flaggedInteractionRepository.save(any(FlaggedInteraction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        flaggedInteractionService.flag(
                organization, null, null, GuardrailType.PROMPT_INJECTION, GuardrailSeverity.HIGH,
                Map.of("pattern", "ignore previous instructions")
        );

        ArgumentCaptor<FlaggedInteraction> captor = ArgumentCaptor.forClass(FlaggedInteraction.class);
        verify(flaggedInteractionRepository).save(captor.capture());

        FlaggedInteraction flagged = captor.getValue();
        assertThat(flagged.getGuardrailType()).isEqualTo(GuardrailType.PROMPT_INJECTION);
        assertThat(flagged.getSeverity()).isEqualTo(GuardrailSeverity.HIGH);
        assertThat(flagged.getDetails()).contains("ignore previous instructions");
        assertThat(flagged.isReviewed()).isFalse();
    }
}
