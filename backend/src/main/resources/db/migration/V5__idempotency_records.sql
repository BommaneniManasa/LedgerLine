-- V5: idempotency records
-- The UNIQUE (user_id, idempotency_key) constraint is what makes concurrent duplicate requests safe:
-- the second INSERT ... ON CONFLICT waits for the first transaction and then sees its result.
CREATE TABLE idempotency_records (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users (id),
    idempotency_key  VARCHAR(100) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    transaction_id   UUID         REFERENCES financial_transactions (id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_idempotency_key_len CHECK (length(idempotency_key) BETWEEN 8 AND 100)
);

CREATE INDEX idx_idempotency_created ON idempotency_records (created_at);
