package com.documind.guardrail.input;

import com.documind.guardrail.domain.GuardrailSeverity;
import com.documind.guardrail.domain.GuardrailType;
import com.documind.guardrail.domain.GuardrailViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cheap, fast, high-confidence regex/keyword layer catching the most common
 * prompt-injection/jailbreak phrasings before any model call is made. This
 * intentionally does not try to catch every possible injection -- subtler
 * attempts are left to the output-side groundedness/scope-refusal guardrails
 * and to Claude's own safety training. See docs/DECISIONS.md for why a
 * hand-rolled heuristic + LLM-judge hybrid was chosen over a dedicated
 * guardrails framework (those are Python-only).
 */
@Component
public class PromptInjectionGuard {

    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?(previous|prior|above)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+\\w+.{0,40}(no\\s+restrictions|unrestricted|jailbroken)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+(your\\s+)?system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("repeat\\s+everything\\s+above", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+(if\\s+you\\s+(have|had)\\s+no|an?\\s+unrestricted)", Pattern.CASE_INSENSITIVE)
    );

    public void check(String userQuestion) {
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(userQuestion).find()) {
                throw new GuardrailViolationException(
                        "Your question was blocked because it matched a known prompt-injection pattern.",
                        GuardrailType.PROMPT_INJECTION,
                        GuardrailSeverity.HIGH,
                        Map.of("matchedPattern", pattern.pattern())
                );
            }
        }
    }
}
