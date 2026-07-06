package com.documind.ingestion.application;

import java.io.InputStream;

/**
 * Port for converting a source document (PDF, etc.) into Markdown text for
 * downstream chunking. Kept as an interface so the initial Tika-based
 * plain-text implementation can be swapped for a layout-aware
 * Markdown-preserving converter later without changing any caller.
 */
public interface TextExtractionService {

    /** Extracts and returns the document's text content as Markdown. Throws DocumentExtractionException on unreadable/corrupt input. */
    String extractToMarkdown(InputStream documentContent);
}
