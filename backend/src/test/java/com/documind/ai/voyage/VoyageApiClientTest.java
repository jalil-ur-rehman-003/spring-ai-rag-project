package com.documind.ai.voyage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VoyageApiClientTest {

    private static final String VOYAGE_EMBEDDINGS_URL = "https://api.voyageai.com/v1/embeddings";
    private static final String TEST_API_KEY = "test-voyage-api-key";
    private static final String TEST_MODEL = "voyage-4";

    private MockRestServiceServer mockServer;
    private VoyageApiClient voyageApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        voyageApiClient = new VoyageApiClient(restClientBuilder.build(), TEST_API_KEY, TEST_MODEL);
    }

    @Test
    void sendsInputTextsAndReturnsEmbeddingsInResponseOrder() {
        mockServer.expect(requestTo(VOYAGE_EMBEDDINGS_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TEST_API_KEY))
                .andExpect(content().json("""
                        {"input": ["first chunk", "second chunk"], "model": "voyage-4"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"embedding": [0.1, 0.2, 0.3], "index": 0},
                            {"embedding": [0.4, 0.5, 0.6], "index": 1}
                          ],
                          "model": "voyage-4",
                          "usage": {"total_tokens": 10}
                        }
                        """, MediaType.APPLICATION_JSON));

        List<float[]> embeddings = voyageApiClient.embed(List.of("first chunk", "second chunk"));

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(embeddings.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        mockServer.verify();
    }

    @Test
    void throwsAVoyageApiExceptionWhenTheApiReturnsAServerError() {
        mockServer.expect(requestTo(VOYAGE_EMBEDDINGS_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> voyageApiClient.embed(List.of("some text")))
                .isInstanceOf(VoyageApiException.class);
    }
}
