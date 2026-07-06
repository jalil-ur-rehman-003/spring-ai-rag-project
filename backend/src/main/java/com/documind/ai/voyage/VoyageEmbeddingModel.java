package com.documind.ai.voyage;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.AbstractEmbeddingModel;

import java.util.List;

/**
 * Adapts Voyage AI's embeddings API to Spring AI's EmbeddingModel contract.
 * Anthropic has no first-party embedding model and officially recommends
 * Voyage AI as its embedding partner -- see docs/DECISIONS.md and the
 * project plan. Delegates the actual HTTP call to VoyageApiClient, kept
 * separate so this class's only job is request/response shape mapping.
 */
public class VoyageEmbeddingModel extends AbstractEmbeddingModel {

    private final VoyageApiClient voyageApiClient;

    public VoyageEmbeddingModel(VoyageApiClient voyageApiClient) {
        this.voyageApiClient = voyageApiClient;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = voyageApiClient.embed(request.getInstructions());

        List<Embedding> embeddings = new java.util.ArrayList<>(vectors.size());
        for (int index = 0; index < vectors.size(); index++) {
            embeddings.add(new Embedding(vectors.get(index), index));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return call(new EmbeddingRequest(List.of(document.getText()), null)).getResult().getOutput();
    }
}
