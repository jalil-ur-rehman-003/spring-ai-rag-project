package com.documind.ai.voyage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoyageEmbeddingModelTest {

    @Mock
    private VoyageApiClient voyageApiClient;

    private VoyageEmbeddingModel voyageEmbeddingModel;

    @BeforeEach
    void setUp() {
        voyageEmbeddingModel = new VoyageEmbeddingModel(voyageApiClient);
    }

    @Test
    void callDelegatesToTheApiClientAndWrapsResultsAsEmbeddingResponse() {
        List<String> instructions = List.of("first chunk", "second chunk");
        when(voyageApiClient.embed(eq(instructions))).thenReturn(List.of(
                new float[]{0.1f, 0.2f}, new float[]{0.3f, 0.4f}
        ));

        EmbeddingResponse response = voyageEmbeddingModel.call(new EmbeddingRequest(instructions, null));

        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().get(0).getOutput()).containsExactly(0.1f, 0.2f);
        assertThat(response.getResults().get(1).getOutput()).containsExactly(0.3f, 0.4f);
    }

    @Test
    void eachResultCarriesItsPositionalIndex() {
        List<String> instructions = List.of("a", "b", "c");
        when(voyageApiClient.embed(eq(instructions))).thenReturn(List.of(
                new float[]{1f}, new float[]{2f}, new float[]{3f}
        ));

        EmbeddingResponse response = voyageEmbeddingModel.call(new EmbeddingRequest(instructions, null));

        List<Integer> indexes = response.getResults().stream().map(Embedding::getIndex).toList();
        assertThat(indexes).containsExactly(0, 1, 2);
    }

    @Test
    void embedSingleDocumentReturnsItsVector() {
        when(voyageApiClient.embed(eq(List.of("solo chunk")))).thenReturn(List.of(new float[]{9f, 8f, 7f}));

        float[] embedding = voyageEmbeddingModel.embed(new org.springframework.ai.document.Document("solo chunk"));

        assertThat(embedding).containsExactly(9f, 8f, 7f);
    }
}
