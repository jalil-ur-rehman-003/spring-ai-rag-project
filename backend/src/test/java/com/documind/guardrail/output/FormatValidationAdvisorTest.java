package com.documind.guardrail.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormatValidationAdvisorTest {

    record Verdict(@JsonProperty("grounded") boolean grounded, @JsonProperty("score") double score) {
    }

    @Mock
    private Function<String, String> retryModelCall;

    private FormatValidationAdvisor formatValidationAdvisor;

    @BeforeEach
    void setUp() {
        formatValidationAdvisor = new FormatValidationAdvisor();
    }

    @Test
    void parsesCleanJsonOnTheFirstAttemptWithoutRetrying() {
        String cleanJson = "{\"grounded\": true, \"score\": 0.92}";

        Optional<Verdict> result = formatValidationAdvisor.parseWithFallback(cleanJson, Verdict.class, retryModelCall);

        assertThat(result).isPresent();
        assertThat(result.get().grounded()).isTrue();
        assertThat(result.get().score()).isEqualTo(0.92);
        verify(retryModelCall, never()).apply(anyString());
    }

    @Test
    void extractsJsonWrappedInProseViaRegexFallback() {
        String wrappedJson = "Here's the verdict: {\"grounded\": false, \"score\": 0.3} -- hope that helps!";

        Optional<Verdict> result = formatValidationAdvisor.parseWithFallback(wrappedJson, Verdict.class, retryModelCall);

        assertThat(result).isPresent();
        assertThat(result.get().grounded()).isFalse();
        verify(retryModelCall, never()).apply(anyString());
    }

    @Test
    void retriesOnceWhenBothStrictParseAndRegexFallbackFail() {
        String garbage = "I cannot provide a structured response right now.";
        String correctedResponse = "{\"grounded\": true, \"score\": 0.75}";
        when(retryModelCall.apply(anyString())).thenReturn(correctedResponse);

        Optional<Verdict> result = formatValidationAdvisor.parseWithFallback(garbage, Verdict.class, retryModelCall);

        assertThat(result).isPresent();
        assertThat(result.get().grounded()).isTrue();
        verify(retryModelCall, times(1)).apply(anyString());
    }

    @Test
    void failsClosedReturningEmptyWhenTheRetryAlsoProducesUnparseableOutput() {
        String garbage = "still not JSON";
        when(retryModelCall.apply(anyString())).thenReturn("also not JSON");

        Optional<Verdict> result = formatValidationAdvisor.parseWithFallback(garbage, Verdict.class, retryModelCall);

        assertThat(result).isEmpty();
        verify(retryModelCall, times(1)).apply(anyString());
    }

    @Test
    void neverRetriesMoreThanOnceEvenAfterFailure() {
        when(retryModelCall.apply(anyString())).thenReturn("still garbage after retry");

        formatValidationAdvisor.parseWithFallback("garbage", Verdict.class, retryModelCall);

        verify(retryModelCall, times(1)).apply(anyString());
    }
}
