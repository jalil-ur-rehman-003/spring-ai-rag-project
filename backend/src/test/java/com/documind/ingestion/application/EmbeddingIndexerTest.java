package com.documind.ingestion.application;

import com.documind.ingestion.domain.DocumentChunkRecord;
import com.documind.ingestion.infrastructure.DocumentChunkJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingIndexerTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private DocumentChunkJdbcRepository documentChunkJdbcRepository;

    private EmbeddingIndexer embeddingIndexer;

    @BeforeEach
    void setUp() {
        embeddingIndexer = new EmbeddingIndexer(embeddingModel, documentChunkJdbcRepository);
    }

    @Test
    void embedsEachStagedChunkAndPersistsItWithItsVector() {
        UUID documentId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        List<StagedDocumentChunk> stagedChunks = List.of(
                new StagedDocumentChunk(documentId, 0, "Chapter 1", "first chunk text"),
                new StagedDocumentChunk(documentId, 1, "Chapter 1 > Section 1.1", "second chunk text")
        );

        when(embeddingModel.embed(List.of("first chunk text", "second chunk text")))
                .thenReturn(List.of(new float[]{0.1f, 0.2f}, new float[]{0.3f, 0.4f}));

        embeddingIndexer.embedAndStore(organizationId, stagedChunks);

        ArgumentCaptor<List<DocumentChunkRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentChunkJdbcRepository).batchInsert(recordsCaptor.capture());

        List<DocumentChunkRecord> insertedRecords = recordsCaptor.getValue();
        assertThat(insertedRecords).hasSize(2);
        assertThat(insertedRecords.get(0).organizationId()).isEqualTo(organizationId);
        assertThat(insertedRecords.get(0).documentId()).isEqualTo(documentId);
        assertThat(insertedRecords.get(0).chunkIndex()).isEqualTo(0);
        assertThat(insertedRecords.get(0).headingPath()).isEqualTo("Chapter 1");
        assertThat(insertedRecords.get(0).content()).isEqualTo("first chunk text");
        assertThat(insertedRecords.get(0).embedding()).containsExactly(0.1f, 0.2f);
        assertThat(insertedRecords.get(1).embedding()).containsExactly(0.3f, 0.4f);
    }

    @Test
    void embeddingAnEmptyChunkListDoesNotCallTheModelOrTheRepository() {
        embeddingIndexer.embedAndStore(UUID.randomUUID(), List.of());

        verify(embeddingModel, org.mockito.Mockito.never()).embed(any(List.class));
        verify(documentChunkJdbcRepository, org.mockito.Mockito.never()).batchInsert(any());
    }
}
