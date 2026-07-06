package com.documind.document.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfig {

    @Bean
    public S3Client s3Client(ObjectStorageProperties objectStorageProperties) {
        return S3Client.builder()
                .endpointOverride(URI.create(objectStorageProperties.endpoint()))
                .region(Region.of(objectStorageProperties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(objectStorageProperties.accessKey(), objectStorageProperties.secretKey())
                ))
                // MinIO (and most non-AWS S3-compatible stores) require path-style access;
                // AWS S3 itself accepts both, so this is safe for both dev and production targets.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
