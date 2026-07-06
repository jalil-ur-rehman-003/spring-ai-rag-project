package com.documind.document.domain;

/** Mirrors the ingestion pipeline stages exactly, so the frontend can render progress from this single column via SSE. */
public enum DocumentStatus {
    PENDING,
    EXTRACTING,
    CHUNKING,
    JSONL_STAGED,
    EMBEDDING,
    READY,
    FAILED
}
