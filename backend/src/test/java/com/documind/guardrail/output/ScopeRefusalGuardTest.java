package com.documind.guardrail.output;

import com.documind.chat.domain.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeRefusalGuardTest {

    private static final double MINIMUM_SIMILARITY_THRESHOLD = 0.5;

    private final ScopeRefusalGuard scopeRefusalGuard = new ScopeRefusalGuard(MINIMUM_SIMILARITY_THRESHOLD);

    @Test
    void isOutOfScopeWhenNoChunksWereRetrievedAtAll() {
        assertThat(scopeRefusalGuard.isOutOfScope(List.of())).isTrue();
    }

    @Test
    void isOutOfScopeWhenEveryRetrievedChunkIsBelowTheSimilarityThreshold() {
        List<RetrievedChunk> lowSimilarityChunks = List.of(
                new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), "unrelated content", null, null, 0.2),
                new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), "also unrelated", null, null, 0.3)
        );

        assertThat(scopeRefusalGuard.isOutOfScope(lowSimilarityChunks)).isTrue();
    }

    @Test
    void isInScopeWhenAtLeastOneChunkMeetsTheSimilarityThreshold() {
        List<RetrievedChunk> mixedChunks = List.of(
                new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), "unrelated content", null, null, 0.2),
                new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), "relevant content", null, null, 0.85)
        );

        assertThat(scopeRefusalGuard.isOutOfScope(mixedChunks)).isFalse();
    }

    @Test
    void providesAStandardRefusalMessage() {
        assertThat(scopeRefusalGuard.buildRefusalMessage()).containsIgnoringCase("don't have");
    }
}
