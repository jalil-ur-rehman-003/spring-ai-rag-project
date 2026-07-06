-- A chat session is either scoped to a single document (document_id set) or
-- to the whole org's document collection (document_id null). Grouping
-- messages under a session is what lets the chat advisor chain carry
-- multi-turn conversational memory.
CREATE TABLE chat_session (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES app_user (id),
    organization_id UUID NOT NULL REFERENCES organization (id),
    document_id     UUID REFERENCES document (id),
    title           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_session_user_id ON chat_session (user_id);
CREATE INDEX idx_chat_session_organization_id ON chat_session (organization_id);

-- Every message in a session, including the assistant's answer with its
-- citations and any guardrail outcomes recorded against it. Keeping
-- guardrail_flags here (rather than only in flagged_interaction) means a
-- single message row is self-describing without a join for the common case
-- of rendering a chat transcript in the UI.
CREATE TABLE chat_message (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES chat_session (id),
    role                TEXT NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content             TEXT NOT NULL,
    citations           JSONB,      -- [{documentId, chunkId, pageNumber, snippet}, ...]
    groundedness_score  NUMERIC,
    guardrail_flags     JSONB,      -- which guardrails fired for this message, if any
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_message_session_id_created_at ON chat_message (session_id, created_at);
