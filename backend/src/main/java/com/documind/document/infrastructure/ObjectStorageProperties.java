package com.documind.document.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds documind.object-storage.* -- an S3-compatible endpoint, MinIO in dev/Compose, real AWS S3 in production. */
@ConfigurationProperties(prefix = "documind.object-storage")
public record ObjectStorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region
) {
}
