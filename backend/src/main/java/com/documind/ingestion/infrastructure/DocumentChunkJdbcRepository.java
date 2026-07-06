package com.documind.ingestion.infrastructure;

import com.documind.ingestion.domain.DocumentChunkRecord;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

/**
 * Direct JDBC batch insert into document_chunk, bypassing JPA/Hibernate --
 * per the plan, pgvector's VECTOR column type isn't natively mapped by
 * Hibernate, and this table needs first-class relational columns
 * (organization_id, document_id, page_number) alongside the vector, which
 * the generic Spring AI VectorStore metadata-map API doesn't give us
 * cleanly. PGvector.toString()'s bracketed-CSV format is what pgvector's
 * JDBC binding (registered via PGvector.addVectorType) expects for the
 * VECTOR column.
 */
@Repository
public class DocumentChunkJdbcRepository {

    private static final String INSERT_SQL = """
            INSERT INTO document_chunk
                (id, document_id, organization_id, chunk_index, content, token_count, page_number, heading_path, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void batchInsert(List<DocumentChunkRecord> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_SQL, chunks, chunks.size(), this::bindChunkParameters);
    }

    private void bindChunkParameters(PreparedStatement statement, DocumentChunkRecord chunk) throws SQLException {
        statement.setObject(1, UUID.randomUUID());
        statement.setObject(2, chunk.documentId());
        statement.setObject(3, chunk.organizationId());
        statement.setInt(4, chunk.chunkIndex());
        statement.setString(5, chunk.content());
        setNullableInt(statement, 6, chunk.tokenCount());
        setNullableInt(statement, 7, chunk.pageNumber());
        statement.setString(8, chunk.headingPath());
        statement.setObject(9, new PGvector(chunk.embedding()));
    }

    private void setNullableInt(PreparedStatement statement, int parameterIndex, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(parameterIndex, Types.INTEGER);
        } else {
            statement.setInt(parameterIndex, value);
        }
    }
}
