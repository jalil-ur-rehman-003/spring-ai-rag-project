package com.documind.chat.infrastructure;

import com.documind.chat.domain.RetrievedChunk;
import com.documind.ingestion.domain.DocumentChunkRecord;
import com.documind.ingestion.infrastructure.DocumentChunkJdbcRepository;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full @SpringBootTest with a real Postgres+pgvector container -- similarity
 * ranking and tenant/document scoping are exactly the kind of behavior a
 * mocked repository can't meaningfully verify.
 */
@Testcontainers
@SpringBootTest
class DocumentChunkRetrievalRepositoryTest {

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
        registry.add("documind.ai.voyage.api-key", () -> "not-needed-for-this-test");
    }

    @Autowired
    private DocumentChunkJdbcRepository documentChunkJdbcRepository;

    @Autowired
    private DocumentChunkRetrievalRepository documentChunkRetrievalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID organizationId;
    private UUID documentId;

    @BeforeEach
    void setUpOrganizationAndDocument() {
        organizationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organization (id, name, plan_tier) VALUES (?, ?, 'FREE')", organizationId, "Test Org " + organizationId);

        UUID adminUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_user (id, organization_id, email, password_hash, role) VALUES (?, ?, ?, 'hash', 'ADMIN')",
                adminUserId, organizationId, "retrieval-test-" + adminUserId + "@acme.test"
        );

        documentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO document (id, organization_id, uploaded_by, original_filename, storage_key, size_bytes, visibility, status)
                VALUES (?, ?, ?, 'test.pdf', 'documents/test.pdf', 100, 'ORG', 'READY')
                """,
                documentId, organizationId, adminUserId
        );
    }

    @Test
    void returnsChunksOrderedByClosestSimilarityToTheQueryVector() {
        // Blending the query direction with an orthogonal direction at increasing weights produces
        // vectors with genuinely different cosine similarity to the query -- two arbitrary one-hot
        // vectors are always exactly orthogonal to each other regardless of index distance, so that
        // naive fixture can't actually distinguish "closer" from "farther".
        float[] queryVector = buildUnitVector(0);
        float[] orthogonalVector = buildUnitVector(1);

        float[] veryCloseVector = blend(queryVector, orthogonalVector, 0.95f);
        float[] somewhatCloseVector = blend(queryVector, orthogonalVector, 0.5f);
        float[] farVector = blend(queryVector, orthogonalVector, 0.05f);

        documentChunkJdbcRepository.batchInsert(List.of(
                new DocumentChunkRecord(documentId, organizationId, 0, "far chunk", null, null, null, farVector),
                new DocumentChunkRecord(documentId, organizationId, 1, "very close chunk", null, null, null, veryCloseVector),
                new DocumentChunkRecord(documentId, organizationId, 2, "somewhat close chunk", null, null, null, somewhatCloseVector)
        ));

        List<RetrievedChunk> results = documentChunkRetrievalRepository.findMostSimilar(
                organizationId, null, queryVector, 3
        );

        assertThat(results).hasSize(3);
        assertThat(results.get(0).content()).isEqualTo("very close chunk");
        assertThat(results.get(1).content()).isEqualTo("somewhat close chunk");
        assertThat(results.get(2).content()).isEqualTo("far chunk");
    }

    @Test
    void scopesResultsToTheGivenDocumentWhenOneIsSpecified() {
        UUID otherDocumentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO document (id, organization_id, uploaded_by, original_filename, storage_key, size_bytes, visibility, status)
                VALUES (?, ?, (SELECT id FROM app_user WHERE organization_id = ?), 'other.pdf', 'documents/other.pdf', 100, 'ORG', 'READY')
                """,
                otherDocumentId, organizationId, organizationId
        );

        float[] queryVector = buildUnitVector(0);
        documentChunkJdbcRepository.batchInsert(List.of(
                new DocumentChunkRecord(documentId, organizationId, 0, "chunk in target document", null, null, null, queryVector),
                new DocumentChunkRecord(otherDocumentId, organizationId, 0, "chunk in a different document", null, null, null, queryVector)
        ));

        List<RetrievedChunk> results = documentChunkRetrievalRepository.findMostSimilar(organizationId, documentId, queryVector, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("chunk in target document");
    }

    @Test
    void neverReturnsChunksFromAnotherOrganizationEvenWithoutADocumentScope() {
        UUID otherOrganizationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organization (id, name, plan_tier) VALUES (?, ?, 'FREE')", otherOrganizationId, "Other Org");
        UUID otherOrgUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_user (id, organization_id, email, password_hash, role) VALUES (?, ?, ?, 'hash', 'ADMIN')",
                otherOrgUserId, otherOrganizationId, "other-org-" + otherOrgUserId + "@acme.test"
        );
        UUID otherOrgDocumentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO document (id, organization_id, uploaded_by, original_filename, storage_key, size_bytes, visibility, status)
                VALUES (?, ?, ?, 'secret.pdf', 'documents/secret.pdf', 100, 'ORG', 'READY')
                """,
                otherOrgDocumentId, otherOrganizationId, otherOrgUserId
        );

        float[] queryVector = buildUnitVector(0);
        documentChunkJdbcRepository.batchInsert(List.of(
                new DocumentChunkRecord(documentId, organizationId, 0, "my org's chunk", null, null, null, queryVector),
                new DocumentChunkRecord(otherOrgDocumentId, otherOrganizationId, 0, "someone else's chunk", null, null, null, queryVector)
        ));

        List<RetrievedChunk> results = documentChunkRetrievalRepository.findMostSimilar(organizationId, null, queryVector, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("my org's chunk");
    }

    private float[] buildUnitVector(int hotIndex) {
        float[] vector = new float[1024];
        Arrays.fill(vector, 0f);
        vector[hotIndex] = 1f;
        return vector;
    }

    /** Weighted blend of two vectors -- higher primaryWeight means the result points closer to primary and farther from secondary. */
    private float[] blend(float[] primary, float[] secondary, float primaryWeight) {
        float[] blended = new float[primary.length];
        for (int i = 0; i < primary.length; i++) {
            blended[i] = primary[i] * primaryWeight + secondary[i] * (1 - primaryWeight);
        }
        return blended;
    }
}
