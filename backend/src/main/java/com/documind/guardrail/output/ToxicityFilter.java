package com.documind.guardrail.output;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Heuristic keyword-blocklist checkpoint on the model's output. Claude's own
 * safety training already covers most harmful content, so this is a policy/
 * brand-safety backstop, not the primary line of defense -- word-boundary
 * matching (not raw substring) so a blocked term embedded inside an
 * unrelated longer word (e.g. "ass" inside "class") doesn't false-positive.
 */
@Component
public class ToxicityFilter {

    private final List<Pattern> blockedTermPatterns;

    public ToxicityFilter(@Value("#{'${documind.guardrail.toxicity.blocked-terms:}'.split(',')}") Collection<String> blockedTerms) {
        this.blockedTermPatterns = blockedTerms.stream()
                .filter(term -> !term.isBlank())
                .map(term -> Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE))
                .toList();
    }

    public boolean isToxic(String text) {
        return blockedTermPatterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}
