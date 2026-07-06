-- Each row is one chunk of a document's Markdown-converted text, plus its
-- embedding vector. organization_id is denormalized here (rather than only
-- reachable via a join through document) so that every similarity search can
-- filter by tenant directly on the table being scanned by the HNSW index --
-- joining out to `document` first would defeat the point of the ANN index.
--
-- Vector dimension (1024) matches Voyage AI's voyage-4 embedding model.
-- If the embedding model changes, this column must be recreated (pgvector
-- dimensions are fixed per-column), not just have its data replaced.
CREATE TABLE document_chunk (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES document (id),
    organization_id UUID NOT NULL REFERENCES organization (id),
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    token_count     INT,
    page_number     INT,
    heading_path    TEXT, -- e.g. "Chapter 3 > Section 3.2", derived from Markdown structure
    embedding       VECTOR(1024) NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HNSW over IVFFlat: no training/build step is required, so chunks can be
-- inserted incrementally as each document finishes ingestion without ever
-- needing an index rebuild. Cosine distance matches Voyage's recommended
-- similarity metric for its embeddings.
CREATE INDEX idx_document_chunk_embedding_hnsw
    ON document_chunk USING hnsw (embedding vector_cosine_ops);

-- Supports the mandatory tenant + document scoping filter applied before/
-- alongside every similarity search.
CREATE INDEX idx_document_chunk_org_document ON document_chunk (organization_id, document_id);
