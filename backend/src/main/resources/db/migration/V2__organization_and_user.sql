-- Organizations are the top-level tenant boundary: every user, document, and
-- chat session belongs to exactly one organization, and query-level tenant
-- scoping (see common.tenant.TenantContext) filters on organization_id.
CREATE TABLE organization (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 TEXT NOT NULL,
    plan_tier            TEXT NOT NULL DEFAULT 'FREE' CHECK (plan_tier IN ('FREE', 'PRO', 'ENTERPRISE')),
    storage_quota_bytes  BIGINT NOT NULL DEFAULT 5368709120, -- 5 GiB default quota
    storage_used_bytes   BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Application users. Named app_user (not "user") to avoid clashing with the
-- reserved SQL keyword in ad-hoc queries and tooling.
CREATE TABLE app_user (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization (id),
    email          CITEXT NOT NULL,
    password_hash  TEXT NOT NULL,
    role           TEXT NOT NULL CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER')),
    status         TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE INDEX idx_app_user_organization_id ON app_user (organization_id);

-- Revocable refresh tokens: stored hashed, never the raw token, so a leaked
-- database dump doesn't hand out reusable credentials. Revocation is a row
-- update, which is why access tokens are kept short-lived and refresh tokens
-- carry the actual "is this session still valid" authority.
CREATE TABLE refresh_token (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES app_user (id),
    token_hash   TEXT NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
