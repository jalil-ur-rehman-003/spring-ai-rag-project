package com.documind.guardrail.domain;

import java.util.Map;

/** Thrown by an input guardrail to short-circuit the chat pipeline before any model call is made. Mapped to HTTP 400 by GlobalExceptionHandler. */
public class GuardrailViolationException extends RuntimeException {

    private final GuardrailType guardrailType;
    private final GuardrailSeverity severity;
    private final Map<String, Object> details;

    public GuardrailViolationException(String message, GuardrailType guardrailType, GuardrailSeverity severity, Map<String, Object> details) {
        super(message);
        this.guardrailType = guardrailType;
        this.severity = severity;
        this.details = details;
    }

    public GuardrailType getGuardrailType() {
        return guardrailType;
    }

    public GuardrailSeverity getSeverity() {
        return severity;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
