package com.documind.guardrail.output;

import com.documind.chat.domain.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refuses to answer when retrieval found nothing relevant, rather than
 * letting the model fall back to its own parametric knowledge and risk an
 * unsourced/hallucinated answer for a document-scoped or org-knowledge
 * question. "Relevant" means at least one retrieved chunk meets the
 * configured minimum cosine similarity.
 */
@Component
public class ScopeRefusalGuard {

    private final double minimumSimilarityThreshold;

    public ScopeRefusalGuard(@Value("${documind.guardrail.scope-refusal.minimum-similarity}") double minimumSimilarityThreshold) {
        this.minimumSimilarityThreshold = minimumSimilarityThreshold;
    }

    public boolean isOutOfScope(List<RetrievedChunk> retrievedChunks) {
        return retrievedChunks.stream().noneMatch(chunk -> chunk.similarityScore() >= minimumSimilarityThreshold);
    }

    public String buildRefusalMessage() {
        return "I don't have enough relevant information in the available documents to answer that question.";
    }
}
