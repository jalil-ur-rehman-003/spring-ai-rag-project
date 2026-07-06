package com.documind.ingestion.application;

import com.documind.document.domain.Document;
import com.documind.document.domain.DocumentStatus;
import com.documind.document.infrastructure.DocumentRepository;
import com.documind.document.infrastructure.ObjectStorageAdapter;
import com.documind.ingestion.domain.IngestionJob;
import com.documind.ingestion.infrastructure.IngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polling worker for the async ingestion pipeline. Runs on a fixed delay
 * (see documind.ingestion.scheduler.poll-interval-ms), claiming PENDING jobs
 * via "FOR UPDATE SKIP LOCKED" so multiple worker threads never double-
 * process the same job (see IngestionJobRepository). Drives the document
 * through PENDING -> EXTRACTING -> CHUNKING -> JSONL_STAGED -> EMBEDDING -> READY.
 *
 * Gated by documind.ingestion.scheduler.enabled (default true) so the whole
 * bean -- and therefore its @Scheduled method -- doesn't exist in test
 * contexts, which set it to false. Without this, the poller keeps firing
 * against a Testcontainers Postgres instance that's already been torn down
 * once its owning @SpringBootTest class finishes, producing noisy
 * "HikariPool ... Connection is not available" errors in later test output.
 */
@Component
@ConditionalOnProperty(name = "documind.ingestion.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class IngestionJobScheduler {

    private static final Logger logger = LoggerFactory.getLogger(IngestionJobScheduler.class);
    private static final int MAX_JOBS_PER_POLL = 10;

    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentRepository documentRepository;
    private final ObjectStorageAdapter objectStorageAdapter;
    private final TextExtractionService textExtractionService;
    private final ChunkingService chunkingService;
    private final ChunkJsonlCodec chunkJsonlCodec;
    private final DocumentProgressPublisher documentProgressPublisher;
    private final EmbeddingIndexer embeddingIndexer;

    public IngestionJobScheduler(
            IngestionJobRepository ingestionJobRepository,
            DocumentRepository documentRepository,
            ObjectStorageAdapter objectStorageAdapter,
            TextExtractionService textExtractionService,
            ChunkingService chunkingService,
            ChunkJsonlCodec chunkJsonlCodec,
            DocumentProgressPublisher documentProgressPublisher,
            EmbeddingIndexer embeddingIndexer
    ) {
        this.ingestionJobRepository = ingestionJobRepository;
        this.documentRepository = documentRepository;
        this.objectStorageAdapter = objectStorageAdapter;
        this.textExtractionService = textExtractionService;
        this.chunkingService = chunkingService;
        this.chunkJsonlCodec = chunkJsonlCodec;
        this.documentProgressPublisher = documentProgressPublisher;
        this.embeddingIndexer = embeddingIndexer;
    }

    @Scheduled(fixedDelayString = "${documind.ingestion.scheduler.poll-interval-ms}")
    @Transactional
    public void processAvailableJobs() {
        List<IngestionJob> availableJobs = ingestionJobRepository.findAvailablePendingJobs(Limit.of(MAX_JOBS_PER_POLL));
        for (IngestionJob job : availableJobs) {
            processJob(job);
        }
    }

    private void processJob(IngestionJob job) {
        Document document = job.getDocument();
        job.markRunning();

        try {
            transitionAndPublish(document, DocumentStatus.EXTRACTING);
            String markdown = textExtractionService.extractToMarkdown(objectStorageAdapter.retrieve(document.getStorageKey()));

            transitionAndPublish(document, DocumentStatus.CHUNKING);
            List<DocumentChunkDraft> chunks = chunkingService.chunk(markdown);

            transitionAndPublish(document, DocumentStatus.JSONL_STAGED);
            String jsonl = chunkJsonlCodec.encode(document.getId(), chunks);
            objectStorageAdapter.storeText(buildJsonlStorageKey(document), jsonl);

            transitionAndPublish(document, DocumentStatus.EMBEDDING);
            List<StagedDocumentChunk> stagedChunks = chunkJsonlCodec.decode(jsonl);
            embeddingIndexer.embedAndStore(document.getOrganization().getId(), stagedChunks);

            transitionAndPublish(document, DocumentStatus.READY);
            job.markSucceeded();
        } catch (RuntimeException exception) {
            String failureReason = "Ingestion failed at stage " + document.getStatus() + ": " + exception.getMessage();
            logger.error("Ingestion job {} failed for document {}", job.getId(), document.getId(), exception);
            document.markFailed(failureReason);
            job.markFailed(failureReason);
            documentProgressPublisher.publish(document.getId(), DocumentStatus.FAILED);
        } finally {
            documentRepository.save(document);
            ingestionJobRepository.save(job);
        }
    }

    private void transitionAndPublish(Document document, DocumentStatus newStatus) {
        document.transitionTo(newStatus);
        documentProgressPublisher.publish(document.getId(), newStatus);
    }

    private String buildJsonlStorageKey(Document document) {
        return document.getStorageKey() + ".chunks.jsonl";
    }
}
