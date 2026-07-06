package com.documind.ingestion.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    // Small limits keep test fixtures readable while still exercising the token-fallback path.
    private final ChunkingService chunkingService = new ChunkingService(20, 4);

    @Test
    void splitsMarkdownAlongHeadingBoundariesWhenSectionsFitWithinTheTokenLimit() {
        String markdown = """
                # Chapter 1
                Short intro paragraph.

                # Chapter 2
                Another short paragraph.
                """;

        List<DocumentChunkDraft> chunks = chunkingService.chunk(markdown);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).headingPath()).isEqualTo("Chapter 1");
        assertThat(chunks.get(0).content()).contains("Short intro paragraph.");
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
        assertThat(chunks.get(1).headingPath()).isEqualTo("Chapter 2");
    }

    @Test
    void tracksNestedHeadingPathsAcrossSubsections() {
        String markdown = """
                # Chapter 3
                Intro to chapter three.

                ## Section 3.2
                Content of section 3.2.
                """;

        List<DocumentChunkDraft> chunks = chunkingService.chunk(markdown);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).headingPath()).isEqualTo("Chapter 3");
        assertThat(chunks.get(1).headingPath()).isEqualTo("Chapter 3 > Section 3.2");
    }

    @Test
    void fallsBackToTokenCountSplittingWhenASectionExceedsTheChunkSizeLimit() {
        String longSectionBody = "word ".repeat(50).trim(); // 50 tokens, well over the 20-token test limit
        String markdown = "# Big Section\n" + longSectionBody;

        List<DocumentChunkDraft> chunks = chunkingService.chunk(markdown);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.headingPath()).isEqualTo("Big Section"));
    }

    @Test
    void consecutiveTokenFallbackChunksOverlap() {
        String longSectionBody = String.join(" ",
                "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
                "twentyone", "twentytwo", "twentythree", "twentyfour", "twentyfive"
        );
        String markdown = "# Overlap Section\n" + longSectionBody;

        List<DocumentChunkDraft> chunks = chunkingService.chunk(markdown);

        assertThat(chunks.size()).isGreaterThan(1);
        String firstChunkContent = chunks.get(0).content();
        String secondChunkContent = chunks.get(1).content();
        String lastWordOfFirstChunk = firstChunkContent.trim().split("\\s+")[firstChunkContent.trim().split("\\s+").length - 1];

        assertThat(secondChunkContent).contains(lastWordOfFirstChunk);
    }

    @Test
    void chunkIndexesAreSequentialAcrossTheWholeDocument() {
        String markdown = """
                # A
                First bit.

                # B
                Second bit.

                # C
                Third bit.
                """;

        List<DocumentChunkDraft> chunks = chunkingService.chunk(markdown);

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
        }
    }

    @Test
    void textBeforeAnyHeadingHasANullHeadingPath() {
        String markdown = "Just a plain paragraph with no heading at all.";

        List<DocumentChunkDraft> chunks = chunkingService.chunk(markdown);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).headingPath()).isNull();
    }

    @Test
    void blankMarkdownProducesNoChunks() {
        assertThat(chunkingService.chunk("   \n\n  ")).isEmpty();
    }
}
