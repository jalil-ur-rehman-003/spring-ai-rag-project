package com.documind.chat.application;

import com.documind.chat.domain.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Formats retrieved chunks into the context block injected ahead of the
 * user's question, and tags each chunk with a "[[chunk:<id>]]" marker the
 * model is instructed to cite. CitationEnforcerAdvisor (Phase 4 guardrail
 * work) parses these markers back out of the model's answer and validates
 * they reference chunks that were actually retrieved for that turn.
 */
@Component
public class RetrievalContextBuilder {

    public String buildContext(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "No relevant content was found in the available documents for this question.";
        }

        StringBuilder context = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            context.append(formatChunk(chunk)).append("\n\n");
        }
        return context.toString().stripTrailing();
    }

    public String buildAugmentedUserMessage(String userQuestion, List<RetrievedChunk> chunks) {
        return """
                Answer the question using only the context below. Cite the sources you used with \
                their [[chunk:<id>]] marker. If the context doesn't contain the answer, say so rather \
                than guessing.

                Context:
                %s

                Question: %s
                """.formatted(buildContext(chunks), userQuestion);
    }

    private String formatChunk(RetrievedChunk chunk) {
        String location = formatLocation(chunk);
        String locationSuffix = location.isBlank() ? "" : " (" + location + ")";
        return "[[chunk:%s]]%s\n%s".formatted(chunk.chunkId(), locationSuffix, chunk.content());
    }

    private String formatLocation(RetrievedChunk chunk) {
        StringBuilder location = new StringBuilder();
        if (chunk.headingPath() != null) {
            location.append(chunk.headingPath());
        }
        if (chunk.pageNumber() != null) {
            if (!location.isEmpty()) {
                location.append(", ");
            }
            location.append("page ").append(chunk.pageNumber());
        }
        return location.toString();
    }
}
