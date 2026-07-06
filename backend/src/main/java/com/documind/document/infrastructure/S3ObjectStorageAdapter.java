package com.documind.document.infrastructure;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Targets any S3-compatible endpoint -- MinIO in dev/Compose, real AWS S3 in production -- via ObjectStorageConfig's endpoint override. */
@Component
public class S3ObjectStorageAdapter implements ObjectStorageAdapter {

    private final S3Client s3Client;
    private final String bucketName;

    public S3ObjectStorageAdapter(S3Client s3Client, ObjectStorageProperties objectStorageProperties) {
        this.s3Client = s3Client;
        this.bucketName = objectStorageProperties.bucket();
    }

    @Override
    public String store(String storageKey, InputStream content, long contentLength, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(content, contentLength));
        return storageKey;
    }

    @Override
    public InputStream retrieve(String storageKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(storageKey).build();
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);
        return response;
    }

    @Override
    public void storeText(String storageKey, String content) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        store(storageKey, new ByteArrayInputStream(contentBytes), contentBytes.length, "text/plain; charset=utf-8");
    }

    @Override
    public String retrieveText(String storageKey) {
        try (InputStream inputStream = retrieve(storageKey)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read text content for storage key: " + storageKey, exception);
        }
    }
}
