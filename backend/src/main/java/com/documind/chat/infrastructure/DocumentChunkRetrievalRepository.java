package com.documind.chat.infrastructure;

import com.documind.chat.domain.RetrievedChunk;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Direct JDBC similarity search against document_chunk's pgvector column,
 * mirroring DocumentChunkJdbcRepository's choice to bypass the generic
 * Spring AI VectorStore API. organization_id is always part of the WHERE
 * clause -- never optional -- since every retrieval must be tenant-scoped;
 * document_id is an additional, optional narrowing for document-scoped
 * chat sessions. Uses pgvector's "<=>" cosine-distance operator, matching
 * the HNSW index's vector_cosine_ops (see V4__document_chunk.sql).
 */
@Repository
public class DocumentChunkRetrievalRepository {

    private static final String FIND_MOST_SIMILAR_SQL = """
            SELECT id, document_id, content, heading_path, page_number, (embedding <=> ?) AS distance
            FROM document_chunk
            WHERE organization_id = ?
              AND (CAST(? AS UUID) IS NULL OR document_id = CAST(? AS UUID))
            ORDER BY embedding <=> ?
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRetrievalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** documentId null searches every READY document's chunks in the organization; non-null scopes to that one document. */
    public List<RetrievedChunk> findMostSimilar(UUID organizationId, UUID documentId, float[] queryEmbedding, int topK) {
        PGvector vectorParameter = new PGvector(queryEmbedding);

        return jdbcTemplate.query(
                FIND_MOST_SIMILAR_SQL,
                (resultSet, rowNumber) -> new RetrievedChunk(
                        (UUID) resultSet.getObject("id"),
                        (UUID) resultSet.getObject("document_id"),
                        resultSet.getString("content"),
                        resultSet.getString("heading_path"),
                        (Integer) resultSet.getObject("page_number"),
                        // Cosine distance -> similarity: pgvector's "<=>" returns distance (0 = identical),
                        // so similarity is 1 - distance for a score where higher means more relevant.
                        1.0 - resultSet.getDouble("distance")
                ),
                vectorParameter, organizationId, documentId, documentId, vectorParameter, topK
        );
    }
}
