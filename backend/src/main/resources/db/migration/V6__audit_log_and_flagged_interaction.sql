-- Append-only trail of every chat exchange and privileged admin action, kept
-- independent of chat_message so it survives even if chat history is ever
-- pruned, and so it can capture non-chat actions (uploads, role changes).
CREATE TABLE audit_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organization (id),
    actor_user_id     UUID REFERENCES app_user (id),
    action            TEXT NOT NULL, -- e.g. CHAT_QUERY, DOCUMENT_UPLOAD, USER_ROLE_CHANGE
    resource_type     TEXT,
    resource_id       UUID,
    request_payload   JSONB,
    response_summary  JSONB,
    ip_address        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_organization_id_created_at ON audit_log (organization_id, created_at);
CREATE INDEX idx_audit_log_actor_user_id ON audit_log (actor_user_id);

-- Every guardrail violation (input or output) gets a row here so guardrail
-- effectiveness/format-drift can be monitored and reviewed independent of
-- the raw audit trail. See guardrail.audit.FlaggedInteractionService.
CREATE TABLE flagged_interaction (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_message_id  UUID REFERENCES chat_message (id),
    document_id      UUID REFERENCES document (id),
    organization_id  UUID NOT NULL REFERENCES organization (id),
    guardrail_type   TEXT NOT NULL, -- PROMPT_INJECTION, PII, TOXICITY, OUT_OF_SCOPE, LOW_GROUNDEDNESS, FORMAT_VIOLATION
    severity         TEXT CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    details          JSONB,
    reviewed         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_flagged_interaction_org_reviewed_created_at
    ON flagged_interaction (organization_id, reviewed, created_at);
