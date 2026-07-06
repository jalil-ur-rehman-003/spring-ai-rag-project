package com.documind.guardrail.output;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Structured verdict requested from the LLM-judge call -- grounded/score/reasoning, parsed via FormatValidationAdvisor. */
public record GroundednessVerdict(
        @JsonProperty("grounded") boolean grounded,
        @JsonProperty("score") double score,
        @JsonProperty("reasoning") String reasoning
) {
}
