package com.documind.ai.config;

import com.documind.ai.voyage.VoyageApiClient;
import com.documind.ai.voyage.VoyageEmbeddingModel;
import com.documind.ai.voyage.VoyageProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Registers the Voyage-backed EmbeddingModel bean. Anthropic has no
 * first-party embedding model, so this stands in for the
 * spring-ai-starter-model-anthropic starter (which only provides a
 * ChatModel) -- see docs/DECISIONS.md.
 */
@Configuration
@EnableConfigurationProperties(VoyageProperties.class)
public class EmbeddingModelConfig {

    @Bean
    public VoyageApiClient voyageApiClient(VoyageProperties voyageProperties) {
        return new VoyageApiClient(RestClient.builder().build(), voyageProperties.apiKey(), voyageProperties.model());
    }

    @Bean
    public EmbeddingModel embeddingModel(VoyageApiClient voyageApiClient) {
        return new VoyageEmbeddingModel(voyageApiClient);
    }
}
