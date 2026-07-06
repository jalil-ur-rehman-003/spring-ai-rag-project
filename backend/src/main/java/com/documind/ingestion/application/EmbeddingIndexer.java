package com.documind.ingestion.application;

import com.documind.ingestion.domain.DocumentChunkRecord;
import com.documind.ingestion.infrastructure.DocumentChunkJdbcRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Bridges the JSONL-staged chunks to their embedded, persisted form. Calls
 * the EmbeddingModel once with all chunk texts (a single batched request,
 * respecting the embedding provider's own per-request batch limits
 * internally) rather than one call per chunk, then writes the vectors to
 * document_chunk via direct JDBC (see DocumentChunkJdbcRepository).
 */
@Component
public class EmbeddingIndexer {

    private final EmbeddingModel embeddingModel;
    private final DocumentChunkJdbcRepository documentChunkJdbcRepository;

    public EmbeddingIndexer(EmbeddingModel embeddingModel, DocumentChunkJdbcRepository documentChunkJdbcRepository) {
        this.embeddingModel = embeddingModel;
        this.documentChunkJdbcRepository = documentChunkJdbcRepository;
    }

    public void embedAndStore(UUID organizationId, List<StagedDocumentChunk> stagedChunks) {
        if (stagedChunks.isEmpty()) {
            return;
        }

        List<String> chunkTexts = stagedChunks.stream().map(StagedDocumentChunk::content).toList();
        List<float[]> embeddings = embeddingModel.embed(chunkTexts);

        List<DocumentChunkRecord> records = new java.util.ArrayList<>(stagedChunks.size());
        for (int i = 0; i < stagedChunks.size(); i++) {
            StagedDocumentChunk stagedChunk = stagedChunks.get(i);
            records.add(new DocumentChunkRecord(
                    stagedChunk.documentId(), organizationId, stagedChunk.chunkIndex(), stagedChunk.content(),
                    null, null, stagedChunk.headingPath(), embeddings.get(i)
            ));
        }

        documentChunkJdbcRepository.batchInsert(records);
    }
}
