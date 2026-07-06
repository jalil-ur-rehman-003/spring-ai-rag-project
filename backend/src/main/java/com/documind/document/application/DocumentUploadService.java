package com.documind.document.application;

import com.documind.auth.domain.User;
import com.documind.document.domain.Document;
import com.documind.document.domain.DocumentVisibility;
import com.documind.document.infrastructure.DocumentRepository;
import com.documind.document.infrastructure.ObjectStorageAdapter;
import com.documind.ingestion.domain.IngestionJob;
import com.documind.ingestion.infrastructure.IngestionJobRepository;
import com.documind.org.domain.Organization;
import com.documind.org.infrastructure.OrganizationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the synchronous, in-request part of document ingestion: validate,
 * store raw bytes, create the Document + IngestionJob rows. Everything after
 * this (extraction, chunking, embedding) happens asynchronously, driven by
 * IngestionJobScheduler picking up the PENDING job this method creates.
 */
@Service
public class DocumentUploadService {

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of("application/pdf");

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final ObjectStorageAdapter objectStorageAdapter;
    private final OrganizationRepository organizationRepository;
    private final long maxUploadSizeBytes;

    public DocumentUploadService(
            DocumentRepository documentRepository,
            IngestionJobRepository ingestionJobRepository,
            ObjectStorageAdapter objectStorageAdapter,
            OrganizationRepository organizationRepository,
            @Value("${documind.documents.max-upload-size-bytes}") long maxUploadSizeBytes
    ) {
        this.documentRepository = documentRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.objectStorageAdapter = objectStorageAdapter;
        this.organizationRepository = organizationRepository;
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    @Transactional
    public Document acceptUpload(
            Organization organization, User uploadedBy, String originalFilename, String contentType,
            InputStream content, long sizeBytes, DocumentVisibility visibility
    ) {
        validateUpload(contentType, sizeBytes);
        // Reserved before storing bytes, so a quota-exceeding upload never reaches object storage
        // or gets counted -- reserveStorage() throws QuotaExceededException without mutating usage
        // if the reservation itself would exceed the quota. Explicitly saved rather than relying on
        // Hibernate dirty-checking: the organization instance passed in may be detached (loaded in a
        // separate call/transaction by the caller), so mutating it in memory alone is not guaranteed
        // to persist without an explicit save.
        organization.reserveStorage(sizeBytes);
        organizationRepository.save(organization);

        String storageKey = buildStorageKey(organization.getId(), originalFilename);
        objectStorageAdapter.store(storageKey, content, sizeBytes, contentType);

        Document document = Document.createPending(organization, uploadedBy, originalFilename, storageKey, contentType, sizeBytes, visibility);
        documentRepository.save(document);

        IngestionJob ingestionJob = IngestionJob.createPendingFor(document);
        ingestionJobRepository.save(ingestionJob);

        return document;
    }

    private void validateUpload(String contentType, long sizeBytes) {
        if (sizeBytes > maxUploadSizeBytes) {
            throw new UnsupportedDocumentFileException(
                    "File size " + sizeBytes + " bytes exceeds the maximum allowed size of " + maxUploadSizeBytes + " bytes"
            );
        }
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new UnsupportedDocumentFileException("Unsupported content type: " + contentType);
        }
    }

    private String buildStorageKey(UUID organizationId, String originalFilename) {
        return "documents/%s/%s/%s".formatted(organizationId, UUID.randomUUID(), originalFilename);
    }
}
