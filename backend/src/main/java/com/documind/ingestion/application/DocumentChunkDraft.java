package com.documind.ingestion.application;

/**
 * A chunk of Markdown text produced by ChunkingService, before it has been
 * embedded or persisted. chunkIndex is the chunk's position within the
 * document, used to preserve ordering once chunks are written to JSONL and
 * later to document_chunk. headingPath reflects the Markdown structure the
 * chunk was split from (e.g. "Chapter 3 > Section 3.2"), or is null if the
 * chunk came from unstructured text with no heading context.
 */
public record DocumentChunkDraft(int chunkIndex, String headingPath, String content) {
}
