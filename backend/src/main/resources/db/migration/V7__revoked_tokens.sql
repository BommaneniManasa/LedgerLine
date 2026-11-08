-- V7: revoked JWT ids (logout). Rows can be purged after expires_at because an expired token is rejected anyway.
CREATE TABLE revoked_tokens (
    jti         VARCHAR(64) PRIMARY KEY,
    user_id     UUID        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens (expires_at);
