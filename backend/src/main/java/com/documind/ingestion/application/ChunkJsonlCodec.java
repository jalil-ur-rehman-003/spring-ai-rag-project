package com.documind.ingestion.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Serializes chunks to/from JSONL (one JSON object per line) for the
 * CHUNKING -> JSONL_STAGED checkpoint. JSONL rather than a single JSON array
 * so the embedding stage can stream line-by-line without holding the whole
 * document's chunks in memory at once, and so the file is inspectable
 * (one grep-able line per chunk) if something needs debugging.
 */
@Component
public class ChunkJsonlCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String encode(UUID documentId, List<DocumentChunkDraft> chunks) {
        StringBuilder jsonl = new StringBuilder();
        for (DocumentChunkDraft chunk : chunks) {
            StagedDocumentChunk stagedChunk = new StagedDocumentChunk(documentId, chunk.chunkIndex(), chunk.headingPath(), chunk.content());
            jsonl.append(writeValueAsString(stagedChunk)).append('\n');
        }
        return jsonl.toString();
    }

    public List<StagedDocumentChunk> decode(String jsonl) {
        List<StagedDocumentChunk> chunks = new ArrayList<>();
        for (String line : jsonl.split("\n")) {
            if (!line.isBlank()) {
                chunks.add(readValue(line));
            }
        }
        return chunks;
    }

    private String writeValueAsString(StagedDocumentChunk stagedChunk) {
        try {
            return objectMapper.writeValueAsString(stagedChunk);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new UncheckedIOException("Failed to serialize a document chunk to JSONL", exception);
        }
    }

    private StagedDocumentChunk readValue(String line) {
        try {
            return objectMapper.readValue(line, StagedDocumentChunk.class);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Failed to parse a JSONL line into a StagedDocumentChunk", exception);
        }
    }
}
