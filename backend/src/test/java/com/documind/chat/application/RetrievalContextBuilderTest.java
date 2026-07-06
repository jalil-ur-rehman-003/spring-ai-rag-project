package com.documind.chat.application;

import com.documind.chat.domain.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalContextBuilderTest {

    private final RetrievalContextBuilder retrievalContextBuilder = new RetrievalContextBuilder();

    @Test
    void includesEachChunksContentAndACitationMarkerReferencingItsId() {
        UUID firstChunkId = UUID.randomUUID();
        UUID secondChunkId = UUID.randomUUID();
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk(firstChunkId, UUID.randomUUID(), "Vacation policy allows 20 days per year.", "Chapter 2 > Leave", 3, 0.92),
                new RetrievedChunk(secondChunkId, UUID.randomUUID(), "Sick leave is unlimited with a doctor's note.", "Chapter 2 > Leave", 4, 0.87)
        );

        String context = retrievalContextBuilder.buildContext(chunks);

        assertThat(context).contains("Vacation policy allows 20 days per year.");
        assertThat(context).contains("Sick leave is unlimited with a doctor's note.");
        assertThat(context).contains("[[chunk:" + firstChunkId + "]]");
        assertThat(context).contains("[[chunk:" + secondChunkId + "]]");
    }

    @Test
    void includesHeadingPathAndPageNumberWhenPresent() {
        UUID chunkId = UUID.randomUUID();
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk(chunkId, UUID.randomUUID(), "Some content.", "Chapter 3 > Section 3.2", 12, 0.9)
        );

        String context = retrievalContextBuilder.buildContext(chunks);

        assertThat(context).contains("Chapter 3 > Section 3.2");
        assertThat(context).contains("page 12");
    }

    @Test
    void omittingHeadingAndPageInformationWhenAbsentStillProducesValidContext() {
        UUID chunkId = UUID.randomUUID();
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk(chunkId, UUID.randomUUID(), "Some content with no location metadata.", null, null, 0.8)
        );

        String context = retrievalContextBuilder.buildContext(chunks);

        assertThat(context).contains("Some content with no location metadata.");
        assertThat(context).contains("[[chunk:" + chunkId + "]]");
    }

    @Test
    void anEmptyChunkListProducesAContextThatSignalsNoRelevantContentWasFound() {
        String context = retrievalContextBuilder.buildContext(List.of());

        assertThat(context).containsIgnoringCase("no relevant");
    }

    @Test
    void buildsAnAugmentedUserMessageCombiningTheOriginalQuestionWithContext() {
        UUID chunkId = UUID.randomUUID();
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk(chunkId, UUID.randomUUID(), "Some policy content.", "Chapter 1", 1, 0.9)
        );

        String augmentedMessage = retrievalContextBuilder.buildAugmentedUserMessage("What is the policy?", chunks);

        assertThat(augmentedMessage).contains("What is the policy?");
        assertThat(augmentedMessage).contains("Some policy content.");
    }
}
