package com.documind.ingestion.application;

import java.util.UUID;

/** A chunk as staged in the JSONL checkpoint file -- the resumable unit read back by the embedding stage. */
public record StagedDocumentChunk(UUID documentId, int chunkIndex, String headingPath, String content) {
}
