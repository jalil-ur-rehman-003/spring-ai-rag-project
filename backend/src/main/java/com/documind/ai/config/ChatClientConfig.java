package com.documind.ai.config;

import com.documind.chat.application.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the AnthropicChatModel (auto-configured by spring-ai-starter-model-anthropic) into a ChatClient with retrieval augmentation as a default advisor on every call. */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel, RetrievalAugmentationAdvisor retrievalAugmentationAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(retrievalAugmentationAdvisor)
                .build();
    }
}
