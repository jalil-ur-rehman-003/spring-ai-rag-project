package com.documind.chat.domain;

import java.util.UUID;

/** One chunk returned by similarity search, close enough to the query embedding to be considered relevant context for the LLM. */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String content,
        String headingPath,
        Integer pageNumber,
        double similarityScore
) {
}
