package com.documind.guardrail.output;

import com.documind.chat.domain.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationEnforcerTest {

    private final CitationEnforcer citationEnforcer = new CitationEnforcer();

    @Test
    void extractsCitedChunkIdsFromTheAnswerText() {
        UUID chunkId = UUID.randomUUID();
        String answer = "Vacation is 20 days per year. [[chunk:" + chunkId + "]]";

        Set<UUID> citedIds = citationEnforcer.extractCitedChunkIds(answer);

        assertThat(citedIds).containsExactly(chunkId);
    }

    @Test
    void extractsMultipleDistinctCitations() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String answer = "See [[chunk:" + firstId + "]] and also [[chunk:" + secondId + "]].";

        Set<UUID> citedIds = citationEnforcer.extractCitedChunkIds(answer);

        assertThat(citedIds).containsExactlyInAnyOrder(firstId, secondId);
    }

    @Test
    void anAnswerWithNoCitationMarkersHasNoValidCitations() {
        boolean valid = citationEnforcer.hasValidCitations("Just a plain answer with no markers.", List.of());

        assertThat(valid).isFalse();
    }

    @Test
    void anAnswerCitingOnlyChunksThatWereActuallyRetrievedIsValid() {
        UUID retrievedChunkId = UUID.randomUUID();
        List<RetrievedChunk> retrievedChunks = List.of(
                new RetrievedChunk(retrievedChunkId, UUID.randomUUID(), "content", null, null, 0.9)
        );
        String answer = "The answer is X. [[chunk:" + retrievedChunkId + "]]";

        assertThat(citationEnforcer.hasValidCitations(answer, retrievedChunks)).isTrue();
    }

    @Test
    void anAnswerCitingAChunkIdThatWasNeverRetrievedIsInvalid() {
        UUID retrievedChunkId = UUID.randomUUID();
        UUID hallucinatedChunkId = UUID.randomUUID();
        List<RetrievedChunk> retrievedChunks = List.of(
                new RetrievedChunk(retrievedChunkId, UUID.randomUUID(), "content", null, null, 0.9)
        );
        String answer = "The answer is X. [[chunk:" + hallucinatedChunkId + "]]";

        assertThat(citationEnforcer.hasValidCitations(answer, retrievedChunks)).isFalse();
    }

    @Test
    void stripsCitationMarkersForDisplayToTheEndUser() {
        UUID chunkId = UUID.randomUUID();
        String answer = "Vacation is 20 days per year. [[chunk:" + chunkId + "]]";

        String displayText = citationEnforcer.stripCitationMarkers(answer);

        assertThat(displayText).isEqualTo("Vacation is 20 days per year.");
    }
}
