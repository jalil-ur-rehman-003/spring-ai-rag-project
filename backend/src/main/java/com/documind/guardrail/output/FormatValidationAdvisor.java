package com.documind.guardrail.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust parsing for structured LLM output (e.g. the groundedness judge's
 * JSON verdict). LLM structured-output adherence is probabilistic, not
 * guaranteed, so every consumer of a "formatted" response needs an explicit
 * fallback chain rather than an assumed-valid parse. Policy, in order:
 *   1. Strict parse of the raw response.
 *   2. Regex-fallback: extract the first {...} block (handles the model
 *      wrapping JSON in prose) and retry the parse against just that.
 *   3. One corrective retry: re-invoke the model with the malformed output
 *      and an explicit instruction to return only valid JSON.
 *   4. Fail closed: if the retry also fails to parse, give up and return
 *      empty -- callers must treat an empty Optional at least as
 *      cautiously as an explicit negative verdict, never as success.
 * Never retries more than once, so a persistently malformed model response
 * doesn't loop and burn cost.
 */
@Component
public class FormatValidationAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(FormatValidationAdvisor.class);
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param rawResponse    the model's raw text response, expected to contain JSON matching targetType
     * @param targetType     the record/class to deserialize into
     * @param retryModelCall invoked with a corrective prompt if the first parse attempt fails; returns the model's retry response
     */
    public <T> Optional<T> parseWithFallback(String rawResponse, Class<T> targetType, Function<String, String> retryModelCall) {
        Optional<T> strictResult = tryParse(rawResponse, targetType);
        if (strictResult.isPresent()) {
            return strictResult;
        }

        Optional<T> regexFallbackResult = tryParse(extractJsonObject(rawResponse), targetType);
        if (regexFallbackResult.isPresent()) {
            return regexFallbackResult;
        }

        logger.warn("Failed to parse structured output on first attempt for target type {}; issuing one corrective retry", targetType.getSimpleName());
        String correctivePrompt = buildCorrectivePrompt(rawResponse, targetType);
        String retryResponse = retryModelCall.apply(correctivePrompt);

        Optional<T> retryResult = tryParse(retryResponse, targetType)
                .or(() -> tryParse(extractJsonObject(retryResponse), targetType));

        if (retryResult.isEmpty()) {
            logger.warn("Corrective retry also failed to produce parseable output for target type {}; failing closed", targetType.getSimpleName());
        }

        return retryResult;
    }

    private <T> Optional<T> tryParse(String text, Class<T> targetType) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(text, targetType));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private String extractJsonObject(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String buildCorrectivePrompt(String malformedResponse, Class<?> targetType) {
        return """
                Your last response didn't match the required JSON schema for %s.
                Your response was: %s
                Return only valid JSON matching that schema, with no surrounding prose.
                """.formatted(targetType.getSimpleName(), malformedResponse);
    }
}
