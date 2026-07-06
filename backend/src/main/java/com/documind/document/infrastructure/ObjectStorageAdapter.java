package com.documind.document.infrastructure;

import java.io.InputStream;

/**
 * Port for raw file storage. Targets an S3-compatible API (MinIO in dev,
 * AWS S3 in prod) so the same implementation works in both environments
 * without code changes -- only the endpoint configuration differs.
 */
public interface ObjectStorageAdapter {

    /** Stores the given content under storageKey and returns that same key, which the caller persists as the pointer. */
    String store(String storageKey, InputStream content, long contentLength, String contentType);

    /** Retrieves previously stored content by its storage key. */
    InputStream retrieve(String storageKey);

    /** Stores a plain-text payload (e.g. the JSONL chunk checkpoint) under storageKey. */
    void storeText(String storageKey, String content);

    /** Retrieves a plain-text payload previously stored via storeText. */
    String retrieveText(String storageKey);
}
