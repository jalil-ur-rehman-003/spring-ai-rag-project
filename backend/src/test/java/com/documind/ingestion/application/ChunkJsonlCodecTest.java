package com.documind.ingestion.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkJsonlCodecTest {

    private final ChunkJsonlCodec chunkJsonlCodec = new ChunkJsonlCodec();

    @Test
    void encodesAndDecodesChunksRoundTrip() {
        UUID documentId = UUID.randomUUID();
        List<DocumentChunkDraft> originalChunks = List.of(
                new DocumentChunkDraft(0, "Chapter 1", "First chunk content."),
                new DocumentChunkDraft(1, "Chapter 1 > Section 1.1", "Second chunk content."),
                new DocumentChunkDraft(2, null, "Third chunk with no heading.")
        );

        String jsonl = chunkJsonlCodec.encode(documentId, originalChunks);
        List<StagedDocumentChunk> decodedChunks = chunkJsonlCodec.decode(jsonl);

        assertThat(decodedChunks).hasSize(3);
        assertThat(decodedChunks.get(0).documentId()).isEqualTo(documentId);
        assertThat(decodedChunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(decodedChunks.get(0).headingPath()).isEqualTo("Chapter 1");
        assertThat(decodedChunks.get(0).content()).isEqualTo("First chunk content.");
        assertThat(decodedChunks.get(2).headingPath()).isNull();
    }

    @Test
    void encodesOneJsonObjectPerLine() {
        UUID documentId = UUID.randomUUID();
        List<DocumentChunkDraft> chunks = List.of(
                new DocumentChunkDraft(0, "A", "first"),
                new DocumentChunkDraft(1, "B", "second")
        );

        String jsonl = chunkJsonlCodec.encode(documentId, chunks);

        assertThat(jsonl.stripTrailing().split("\n")).hasSize(2);
    }

    @Test
    void decodingBlankInputProducesNoChunks() {
        assertThat(chunkJsonlCodec.decode("")).isEmpty();
        assertThat(chunkJsonlCodec.decode("   \n  \n")).isEmpty();
    }
}
