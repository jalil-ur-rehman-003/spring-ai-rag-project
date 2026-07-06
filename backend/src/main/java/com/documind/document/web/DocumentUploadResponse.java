package com.documind.document.web;

import com.documind.document.domain.DocumentStatus;

import java.util.UUID;

public record DocumentUploadResponse(UUID documentId, DocumentStatus status) {
}
