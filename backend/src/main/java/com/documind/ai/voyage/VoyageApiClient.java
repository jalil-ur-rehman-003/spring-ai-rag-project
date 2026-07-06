package com.documind.ai.voyage;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Thin HTTP client for Voyage AI's embeddings endpoint. Kept separate from
 * VoyageEmbeddingModel so the Spring AI adapter logic (request/response
 * shape mapping to EmbeddingModel's contract) is testable independently of
 * the raw HTTP concern, and so this class alone is what needs mocking in
 * tests that don't care about HTTP specifics.
 */
public class VoyageApiClient {

    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public VoyageApiClient(RestClient restClient, String apiKey, String model) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    /** Returns one embedding vector per input text, in the same order as the input list. */
    public List<float[]> embed(List<String> texts) {
        try {
            VoyageEmbeddingsRequest request = new VoyageEmbeddingsRequest(texts, model);
            VoyageEmbeddingsResponse response = restClient.post()
                    .uri("https://api.voyageai.com" + EMBEDDINGS_PATH)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(VoyageEmbeddingsResponse.class);

            return response.data().stream()
                    .sorted((a, b) -> Integer.compare(a.index(), b.index()))
                    .map(VoyageEmbeddingsResponse.EmbeddingData::embedding)
                    .toList();
        } catch (RestClientException exception) {
            throw new VoyageApiException("Voyage AI embeddings request failed", exception);
        }
    }

    private record VoyageEmbeddingsRequest(List<String> input, String model) {
    }

    private record VoyageEmbeddingsResponse(List<EmbeddingData> data, String model) {
        private record EmbeddingData(float[] embedding, int index) {
        }
    }
}
