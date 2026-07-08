package com.documind.document.web;

import com.documind.document.domain.DocumentStatus;
import com.documind.document.domain.DocumentVisibility;

import java.time.Instant;
import java.util.UUID;

public record DocumentListItemResponse(
        UUID documentId,
        String title,
        String originalFilename,
        long sizeBytes,
        DocumentVisibility visibility,
        DocumentStatus status,
        String failureReason,
        Instant createdAt
) {
}
