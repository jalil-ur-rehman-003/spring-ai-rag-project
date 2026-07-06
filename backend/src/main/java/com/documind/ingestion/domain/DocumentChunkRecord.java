package com.documind.ingestion.domain;

import java.util.UUID;

/**
 * A chunk ready to be persisted to document_chunk, embedding included. Not a
 * JPA entity -- see EmbeddingIndexer/DocumentChunkJdbcRepository -- because
 * pgvector's VECTOR column type isn't natively mapped by Hibernate without
 * an extra type-mapping dependency, and the plan calls for a direct JDBC
 * batch insert here rather than the generic Spring AI VectorStore API,
 * since we need first-class organization_id/document_id/page_number columns
 * alongside the vector.
 */
public record DocumentChunkRecord(
        UUID documentId,
        UUID organizationId,
        int chunkIndex,
        String content,
        Integer tokenCount,
        Integer pageNumber,
        String headingPath,
        float[] embedding
) {
}
