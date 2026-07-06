package com.documind.guardrail.domain;

public enum GuardrailType {
    PROMPT_INJECTION,
    PII,
    TOXICITY,
    OUT_OF_SCOPE,
    LOW_GROUNDEDNESS,
    FORMAT_VIOLATION,
    RATE_LIMIT_EXCEEDED,
    UNSUPPORTED_FILE
}
