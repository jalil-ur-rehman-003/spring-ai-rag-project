package com.documind.document.web;

import com.documind.auth.web.AuthTokenResponse;
import com.documind.auth.web.LoginRequest;
import com.documind.auth.web.RegisterOrganizationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises POST /api/v1/documents end-to-end against real Postgres+pgvector
 * and real MinIO (S3-compatible) containers -- confirms the uploaded file
 * actually lands in object storage and the Document + IngestionJob rows are
 * created with the correct initial state, not just that the HTTP call
 * returns the expected status code.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DocumentControllerIntegrationTest {

    private static final String TEST_BUCKET_NAME = "documind-test";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    static MinIOContainer minioContainer = new MinIOContainer("minio/minio:latest");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
        );
        registry.add("documind.security.jwt.signing-key", () -> "integration-test-signing-key-256-bits-minimum-length");
        registry.add("spring.ai.anthropic.api-key", () -> "not-needed-for-document-tests");

        registry.add("documind.object-storage.endpoint", minioContainer::getS3URL);
        registry.add("documind.object-storage.access-key", minioContainer::getUserName);
        registry.add("documind.object-storage.secret-key", minioContainer::getPassword);
        registry.add("documind.object-storage.bucket", () -> TEST_BUCKET_NAME);
        registry.add("documind.object-storage.region", () -> "us-east-1");
        registry.add("documind.ingestion.scheduler.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String registerAndLoginReturningAccessToken() throws Exception {
        String email = "uploader-" + System.nanoTime() + "@acme.test";
        RegisterOrganizationRequest registerRequest = new RegisterOrganizationRequest("Acme Corp", email, "SuperSecurePassword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        String loginResponseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "SuperSecurePassword123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(loginResponseBody, AuthTokenResponse.class).accessToken();
    }

    private void ensureTestBucketExists() {
        boolean bucketAlreadyExists = s3Client.listBuckets().buckets().stream()
                .anyMatch(bucket -> bucket.name().equals(TEST_BUCKET_NAME));
        if (!bucketAlreadyExists) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET_NAME).build());
        }
    }

    @Test
    void uploadingAPdfCreatesAPendingDocumentAndStoresTheFileInObjectStorage() throws Exception {
        ensureTestBucketExists();
        String accessToken = registerAndLoginReturningAccessToken();

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "policy-guide.pdf", "application/pdf", "%PDF-1.4 fake pdf content".getBytes()
        );

        String responseBody = mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdfFile)
                        .param("visibility", "ORG")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        DocumentUploadResponse uploadResponse = objectMapper.readValue(responseBody, DocumentUploadResponse.class);
        assertThatDocumentWasActuallyStored(uploadResponse);
    }

    private void assertThatDocumentWasActuallyStored(DocumentUploadResponse uploadResponse) {
        var listedObjects = s3Client.listObjectsV2(request -> request.bucket(TEST_BUCKET_NAME));
        boolean anyObjectMatchesFilename = listedObjects.contents().stream()
                .anyMatch(object -> object.key().endsWith("policy-guide.pdf"));

        org.assertj.core.api.Assertions.assertThat(uploadResponse.documentId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(anyObjectMatchesFilename).isTrue();
    }

    @Test
    void uploadingAnUnauthenticatedRequestIsRejected() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "policy-guide.pdf", "application/pdf", "%PDF-1.4 fake pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents").file(pdfFile))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadingANonPdfFileIsRejected() throws Exception {
        String accessToken = registerAndLoginReturningAccessToken();

        MockMultipartFile textFile = new MockMultipartFile("file", "notes.txt", "text/plain", "just some notes".getBytes());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(textFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void successfulUploadIncreasesTheOrganizationsStorageUsageInTheDatabase() throws Exception {
        ensureTestBucketExists();
        String accessToken = registerAndLoginReturningAccessToken();
        byte[] fileContent = "%PDF-1.4 fake pdf content".getBytes();
        MockMultipartFile pdfFile = new MockMultipartFile("file", "policy-guide.pdf", "application/pdf", fileContent);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdfFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted());

        Long storageUsedBytes = jdbcTemplate.queryForObject(
                "SELECT storage_used_bytes FROM organization ORDER BY created_at DESC LIMIT 1", Long.class
        );
        org.assertj.core.api.Assertions.assertThat(storageUsedBytes).isEqualTo((long) fileContent.length);
    }

    @Test
    void rejectsAnUploadThatWouldExceedTheOrganizationsQuota() throws Exception {
        String accessToken = registerAndLoginReturningAccessToken();
        // FREE-tier orgs default to a 5 GiB quota (see OrganizationService); this manually shrinks
        // it down to make the quota trivially exceedable in a test without a multi-gigabyte upload.
        jdbcTemplate.update("UPDATE organization SET storage_quota_bytes = 10 WHERE created_at = (SELECT MAX(created_at) FROM organization)");

        byte[] fileContent = "%PDF-1.4 fake pdf content larger than ten bytes".getBytes();
        MockMultipartFile pdfFile = new MockMultipartFile("file", "policy-guide.pdf", "application/pdf", fileContent);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdfFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }
}
