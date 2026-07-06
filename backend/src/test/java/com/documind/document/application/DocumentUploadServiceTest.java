package com.documind.document.application;

import com.documind.auth.domain.User;
import com.documind.auth.domain.UserRole;
import com.documind.document.domain.Document;
import com.documind.document.domain.DocumentStatus;
import com.documind.document.domain.DocumentVisibility;
import com.documind.document.infrastructure.DocumentRepository;
import com.documind.document.infrastructure.ObjectStorageAdapter;
import com.documind.ingestion.domain.IngestionJob;
import com.documind.ingestion.infrastructure.IngestionJobRepository;
import com.documind.org.domain.Organization;
import com.documind.org.domain.PlanTier;
import com.documind.org.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

    private static final long MAX_UPLOAD_SIZE_BYTES = 50L * 1024 * 1024;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private ObjectStorageAdapter objectStorageAdapter;

    @Mock
    private OrganizationRepository organizationRepository;

    private DocumentUploadService documentUploadService;
    private Organization organization;
    private User uploader;

    @BeforeEach
    void setUp() {
        documentUploadService = new DocumentUploadService(
                documentRepository, ingestionJobRepository, objectStorageAdapter, organizationRepository, MAX_UPLOAD_SIZE_BYTES
        );
        organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
        uploader = User.createNew(organization, "admin@acme.test", "irrelevant-hash", UserRole.ADMIN);
    }

    @Test
    void acceptingAValidPdfStoresItAndCreatesADocumentAndPendingIngestionJob() {
        byte[] fileContent = "%PDF-1.4 fake pdf content".getBytes();
        when(objectStorageAdapter.store(anyString(), any(), anyLong(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document createdDocument = documentUploadService.acceptUpload(
                organization, uploader, "policy-guide.pdf", "application/pdf",
                new ByteArrayInputStream(fileContent), fileContent.length, DocumentVisibility.ORG
        );

        assertThat(createdDocument.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(createdDocument.getOriginalFilename()).isEqualTo("policy-guide.pdf");
        assertThat(createdDocument.getOrganization()).isEqualTo(organization);
        assertThat(createdDocument.getUploadedBy()).isEqualTo(uploader);

        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);
        verify(ingestionJobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getDocument()).isEqualTo(createdDocument);
    }

    @Test
    void rejectsFilesLargerThanTheConfiguredLimit() {
        long oversizedLength = MAX_UPLOAD_SIZE_BYTES + 1;

        assertThatThrownBy(() -> documentUploadService.acceptUpload(
                organization, uploader, "huge.pdf", "application/pdf",
                new ByteArrayInputStream(new byte[0]), oversizedLength, DocumentVisibility.ORG
        )).isInstanceOf(UnsupportedDocumentFileException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void rejectsNonPdfContentTypes() {
        byte[] fileContent = "not a pdf".getBytes();

        assertThatThrownBy(() -> documentUploadService.acceptUpload(
                organization, uploader, "notes.txt", "text/plain",
                new ByteArrayInputStream(fileContent), fileContent.length, DocumentVisibility.ORG
        )).isInstanceOf(UnsupportedDocumentFileException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void rejectsAnUploadThatWouldExceedTheOrganizationsStorageQuota() {
        // organization's quota is 1024 bytes total (see setUp); this upload alone exceeds it.
        byte[] fileContent = new byte[2000];

        assertThatThrownBy(() -> documentUploadService.acceptUpload(
                organization, uploader, "policy-guide.pdf", "application/pdf",
                new ByteArrayInputStream(fileContent), fileContent.length, DocumentVisibility.ORG
        )).isInstanceOf(com.documind.org.application.QuotaExceededException.class);

        verify(objectStorageAdapter, org.mockito.Mockito.never()).store(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void acceptingAnUploadWithinQuotaIncreasesTheOrganizationsStorageUsage() {
        byte[] fileContent = "%PDF-1.4 fake pdf content".getBytes();
        when(objectStorageAdapter.store(anyString(), any(), anyLong(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        long storageUsedBefore = organization.getStorageUsedBytes();

        documentUploadService.acceptUpload(
                organization, uploader, "policy-guide.pdf", "application/pdf",
                new ByteArrayInputStream(fileContent), fileContent.length, DocumentVisibility.ORG
        );

        assertThat(organization.getStorageUsedBytes()).isEqualTo(storageUsedBefore + fileContent.length);
    }
}
