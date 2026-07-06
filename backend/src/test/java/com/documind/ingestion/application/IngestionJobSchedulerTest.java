package com.documind.ingestion.application;

import com.documind.auth.domain.User;
import com.documind.auth.domain.UserRole;
import com.documind.document.domain.Document;
import com.documind.document.domain.DocumentStatus;
import com.documind.document.domain.DocumentVisibility;
import com.documind.document.infrastructure.DocumentRepository;
import com.documind.document.infrastructure.ObjectStorageAdapter;
import com.documind.ingestion.domain.IngestionJob;
import com.documind.ingestion.domain.IngestionJobStatus;
import com.documind.ingestion.infrastructure.IngestionJobRepository;
import com.documind.org.domain.Organization;
import com.documind.org.domain.PlanTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionJobSchedulerTest {

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ObjectStorageAdapter objectStorageAdapter;

    @Mock
    private TextExtractionService textExtractionService;

    @Mock
    private EmbeddingIndexer embeddingIndexer;

    private ChunkingService chunkingService;
    private ChunkJsonlCodec chunkJsonlCodec;
    private DocumentProgressPublisher documentProgressPublisher;
    private IngestionJobScheduler ingestionJobScheduler;

    private Document document;
    private IngestionJob ingestionJob;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService(650, 100);
        chunkJsonlCodec = new ChunkJsonlCodec();
        documentProgressPublisher = new DocumentProgressPublisher();
        ingestionJobScheduler = new IngestionJobScheduler(
                ingestionJobRepository, documentRepository, objectStorageAdapter, textExtractionService,
                chunkingService, chunkJsonlCodec, documentProgressPublisher, embeddingIndexer
        );

        Organization organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
        User uploader = User.createNew(organization, "admin@acme.test", "irrelevant-hash", UserRole.ADMIN);
        document = Document.createPending(
                organization, uploader, "policy-guide.pdf", "documents/org/doc/policy-guide.pdf",
                "application/pdf", 1024, DocumentVisibility.ORG
        );
        ingestionJob = IngestionJob.createPendingFor(document);
    }

    @Test
    void processingAJobTakesTheDocumentThroughEveryStageToReady() {
        when(ingestionJobRepository.findAvailablePendingJobs(any())).thenReturn(List.of(ingestionJob));
        when(objectStorageAdapter.retrieve(document.getStorageKey())).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(textExtractionService.extractToMarkdown(any())).thenReturn("# Policy Overview\nThis is the policy body text.");

        List<DocumentStatus> publishedStatuses = new java.util.concurrent.CopyOnWriteArrayList<>();
        documentProgressPublisher.subscribe(document.getId(), publishedStatuses::add);

        ingestionJobScheduler.processAvailableJobs();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(ingestionJob.getStatus()).isEqualTo(IngestionJobStatus.SUCCEEDED);
        verify(objectStorageAdapter).storeText(eq(document.getStorageKey() + ".chunks.jsonl"), anyString());
        verify(embeddingIndexer).embedAndStore(eq(document.getOrganization().getId()), any());
        assertThat(publishedStatuses).containsExactly(
                DocumentStatus.EXTRACTING, DocumentStatus.CHUNKING, DocumentStatus.JSONL_STAGED,
                DocumentStatus.EMBEDDING, DocumentStatus.READY
        );
    }

    @Test
    void aFailureDuringExtractionMarksTheDocumentAndJobAsFailedRatherThanLeavingThemStuck() {
        when(ingestionJobRepository.findAvailablePendingJobs(any())).thenReturn(List.of(ingestionJob));
        when(objectStorageAdapter.retrieve(document.getStorageKey())).thenReturn(new ByteArrayInputStream(new byte[0]));
        doThrow(new DocumentExtractionException("corrupt file", new RuntimeException()))
                .when(textExtractionService).extractToMarkdown(any());

        List<DocumentStatus> publishedStatuses = new java.util.concurrent.CopyOnWriteArrayList<>();
        documentProgressPublisher.subscribe(document.getId(), publishedStatuses::add);

        ingestionJobScheduler.processAvailableJobs();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getFailureReason()).isNotBlank();
        assertThat(ingestionJob.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
        assertThat(ingestionJob.getLastError()).isNotBlank();
        assertThat(publishedStatuses).containsExactly(DocumentStatus.EXTRACTING, DocumentStatus.FAILED);
    }

    @Test
    void processingWithNoAvailableJobsDoesNothing() {
        when(ingestionJobRepository.findAvailablePendingJobs(any())).thenReturn(List.of());

        ingestionJobScheduler.processAvailableJobs();

        verify(objectStorageAdapter, org.mockito.Mockito.never()).retrieve(anyString());
    }
}
