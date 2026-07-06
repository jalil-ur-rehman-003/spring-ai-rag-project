package com.documind.ingestion.infrastructure;

import com.documind.ingestion.domain.DocumentChunkRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full @SpringBootTest (not a slice test) because DocumentChunkJdbcRepository
 * depends on Flyway having created the document_chunk table with its VECTOR
 * column -- a plain @JdbcTest with an embedded/H2-style database wouldn't
 * have the pgvector extension available at all.
 */
@Testcontainers
@SpringBootTest
class DocumentChunkJdbcRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
        );
        registry.add("documind.security.jwt.signing-key", () -> "integration-test-signing-key-256-bits-minimum-length");
        registry.add("spring.ai.anthropic.api-key", () -> "not-needed-for-this-test");
        registry.add("documind.object-storage.secret-key", () -> "test-secret-key-not-actually-used");
        registry.add("documind.ingestion.scheduler.enabled", () -> "false");
    }

    @Autowired
    private DocumentChunkJdbcRepository documentChunkJdbcRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID organizationId;
    private UUID documentId;

    @BeforeEach
    void setUpOrganizationAndDocument() {
        organizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organization (id, name, plan_tier) VALUES (?, ?, 'FREE')",
                organizationId, "Test Org " + organizationId
        );

        UUID adminUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_user (id, organization_id, email, password_hash, role) VALUES (?, ?, ?, 'hash', 'ADMIN')",
                adminUserId, organizationId, "chunk-test-" + adminUserId + "@acme.test"
        );

        documentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO document (id, organization_id, uploaded_by, original_filename, storage_key, size_bytes, visibility, status)
                VALUES (?, ?, ?, 'test.pdf', 'documents/test.pdf', 100, 'ORG', 'EMBEDDING')
                """,
                documentId, organizationId, adminUserId
        );
    }

    @Test
    void batchInsertsChunksAndTheyAreReadableBackWithTheirVectors() {
        float[] firstEmbedding = buildEmbeddingVector(0.1f);
        float[] secondEmbedding = buildEmbeddingVector(0.9f);

        List<DocumentChunkRecord> chunks = List.of(
                new DocumentChunkRecord(documentId, organizationId, 0, "first chunk content", 5, 1, "Chapter 1", firstEmbedding),
                new DocumentChunkRecord(documentId, organizationId, 1, "second chunk content", 6, 1, "Chapter 1 > Section 1.1", secondEmbedding)
        );

        documentChunkJdbcRepository.batchInsert(chunks);

        List<Map<String, Object>> storedRows = jdbcTemplate.queryForList(
                "SELECT chunk_index, content, heading_path FROM document_chunk WHERE document_id = ? ORDER BY chunk_index",
                documentId
        );

        assertThat(storedRows).hasSize(2);
        assertThat(storedRows.get(0).get("content")).isEqualTo("first chunk content");
        assertThat(storedRows.get(0).get("heading_path")).isEqualTo("Chapter 1");
        assertThat(storedRows.get(1).get("heading_path")).isEqualTo("Chapter 1 > Section 1.1");

        Integer embeddingDimensions = jdbcTemplate.queryForObject(
                "SELECT vector_dims(embedding) FROM document_chunk WHERE document_id = ? AND chunk_index = 0",
                Integer.class, documentId
        );
        assertThat(embeddingDimensions).isEqualTo(firstEmbedding.length);
    }

    @Test
    void batchInsertingAnEmptyListDoesNothing() {
        documentChunkJdbcRepository.batchInsert(List.of());

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk WHERE document_id = ?", Integer.class, documentId
        );
        assertThat(rowCount).isZero();
    }

    private float[] buildEmbeddingVector(float fillValue) {
        float[] vector = new float[1024];
        java.util.Arrays.fill(vector, fillValue);
        return vector;
    }
}
