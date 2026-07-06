-- A Document is the metadata record for an uploaded file. The raw bytes live
-- in object storage (see document.infrastructure.ObjectStorageAdapter);
-- storage_key is the pointer into that bucket, not the content itself.
CREATE TABLE document (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID NOT NULL REFERENCES organization (id),
    uploaded_by        UUID NOT NULL REFERENCES app_user (id),
    title              TEXT,
    original_filename  TEXT NOT NULL,
    storage_key        TEXT NOT NULL,
    content_type       TEXT,
    size_bytes         BIGINT NOT NULL,
    visibility         TEXT NOT NULL CHECK (visibility IN ('PRIVATE', 'ORG')),
    -- Status mirrors the ingestion pipeline stages exactly, so the frontend
    -- can render a progress step from this single column via SSE.
    status             TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'EXTRACTING', 'CHUNKING', 'JSONL_STAGED', 'EMBEDDING', 'READY', 'FAILED')),
    failure_reason     TEXT,
    page_count         INT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_organization_id_status ON document (organization_id, status);
CREATE INDEX idx_document_uploaded_by ON document (uploaded_by);

-- The async work queue: one row per document ingestion attempt. Workers claim
-- rows with "SELECT ... FOR UPDATE SKIP LOCKED" so multiple worker threads
-- (or, later, multiple app instances) never double-process the same job.
CREATE TABLE ingestion_job (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id    UUID NOT NULL REFERENCES document (id),
    status         TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    attempt_count  INT NOT NULL DEFAULT 0,
    last_error     TEXT,
    locked_by      TEXT, -- worker/instance identifier, for diagnosing stuck jobs
    locked_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ingestion_job_status_created_at ON ingestion_job (status, created_at);
CREATE INDEX idx_ingestion_job_document_id ON ingestion_job (document_id);
