package com.documind.guardrail.input;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Regex-based PII detection/redaction applied to user input before it's
 * embedded or logged. Deliberately conservative pattern set (email, SSN,
 * credit card, phone) covering the common, high-confidence cases; this is
 * the same heuristic-first tier as PromptInjectionGuard, not a
 * comprehensive PII/NER solution.
 */
@Component
public class PiiRedactionGuard {

    // LinkedHashMap preserves iteration order -- credit card numbers must be checked before phone
    // numbers, since a 16-digit card number could otherwise partially match a looser phone pattern.
    private static final Map<Pattern, String> REDACTION_PATTERNS = new LinkedHashMap<>();

    static {
        REDACTION_PATTERNS.put(Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b"), "[REDACTED_EMAIL]");
        REDACTION_PATTERNS.put(Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), "[REDACTED_SSN]");
        REDACTION_PATTERNS.put(Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b"), "[REDACTED_CREDIT_CARD]");
        REDACTION_PATTERNS.put(Pattern.compile("\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]?\\d{4}\\b"), "[REDACTED_PHONE]");
    }

    public String redact(String text) {
        String result = text;
        for (Map.Entry<Pattern, String> entry : REDACTION_PATTERNS.entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        return result;
    }

    public boolean containsPii(String text) {
        return REDACTION_PATTERNS.keySet().stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}
