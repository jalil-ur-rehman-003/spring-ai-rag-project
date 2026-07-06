package com.documind.ai.voyage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds documind.ai.voyage.* -- the Voyage AI API key and embedding model name. */
@ConfigurationProperties(prefix = "documind.ai.voyage")
public record VoyageProperties(String apiKey, String model) {
}
