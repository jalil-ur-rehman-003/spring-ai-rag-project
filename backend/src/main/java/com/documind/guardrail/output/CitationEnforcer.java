package com.documind.guardrail.output;

import com.documind.chat.domain.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Citation validation is never purely LLM-self-reported: this deterministically
 * parses the [[chunk:<id>]] markers RetrievalContextBuilder instructs the
 * model to emit, and cross-checks each cited id against the chunk set that
 * was actually retrieved for that turn. An answer citing a chunk id that
 * was never retrieved (a hallucinated citation) is treated the same as an
 * answer with no citations at all -- both fail the guardrail.
 */
@Component
public class CitationEnforcer {

    private static final Pattern CITATION_MARKER_PATTERN = Pattern.compile("\\[\\[chunk:([0-9a-fA-F-]{36})]]");

    public Set<UUID> extractCitedChunkIds(String answerText) {
        Matcher matcher = CITATION_MARKER_PATTERN.matcher(answerText);
        Set<UUID> citedIds = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            citedIds.add(UUID.fromString(matcher.group(1)));
        }
        return citedIds;
    }

    public boolean hasValidCitations(String answerText, List<RetrievedChunk> retrievedChunks) {
        Set<UUID> citedIds = extractCitedChunkIds(answerText);
        if (citedIds.isEmpty()) {
            return false;
        }

        Set<UUID> retrievedIds = retrievedChunks.stream().map(RetrievedChunk::chunkId).collect(Collectors.toSet());
        return retrievedIds.containsAll(citedIds);
    }

    public String stripCitationMarkers(String answerText) {
        return CITATION_MARKER_PATTERN.matcher(answerText).replaceAll("").stripTrailing();
    }
}
