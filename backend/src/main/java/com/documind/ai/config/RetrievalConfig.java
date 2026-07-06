package com.documind.ai.config;

import com.documind.chat.application.RetrievalContextBuilder;
import com.documind.chat.application.RetrievalAugmentationAdvisor;
import com.documind.chat.infrastructure.DocumentChunkRetrievalRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetrievalConfig {

    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            DocumentChunkRetrievalRepository documentChunkRetrievalRepository,
            EmbeddingModel embeddingModel,
            RetrievalContextBuilder retrievalContextBuilder,
            @Value("${documind.chat.retrieval.top-k}") int topK
    ) {
        return new RetrievalAugmentationAdvisor(documentChunkRetrievalRepository, embeddingModel, retrievalContextBuilder, topK);
    }
}
