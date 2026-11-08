-- V3: financial transactions
-- Convention for every transaction: money leaves source_account_id (DEBIT) and enters destination_account_id (CREDIT).
--   DEPOSIT    : source = system clearing account, destination = customer account
--   WITHDRAWAL : source = customer account,        destination = system clearing account
--   TRANSFER   : source = customer account,        destination = customer account
CREATE TABLE financial_transactions (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    reference               VARCHAR(30)   NOT NULL,
    type                    VARCHAR(10)   NOT NULL,
    status                  VARCHAR(10)   NOT NULL,
    source_account_id       UUID          NOT NULL REFERENCES accounts (id),
    destination_account_id  UUID          NOT NULL REFERENCES accounts (id),
    amount                  NUMERIC(19,2) NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    description             VARCHAR(200),
    initiated_by            UUID          NOT NULL REFERENCES users (id),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_txn_reference    UNIQUE (reference),
    CONSTRAINT chk_txn_type        CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_txn_status      CHECK (status IN ('COMPLETED', 'FAILED')),
    CONSTRAINT chk_txn_amount      CHECK (amount > 0),
    CONSTRAINT chk_txn_currency    CHECK (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT chk_txn_accounts    CHECK (source_account_id <> destination_account_id)
);

CREATE INDEX idx_txn_source      ON financial_transactions (source_account_id, created_at DESC);
CREATE INDEX idx_txn_destination ON financial_transactions (destination_account_id, created_at DESC);
CREATE INDEX idx_txn_created     ON financial_transactions (created_at DESC);
CREATE INDEX idx_txn_initiator   ON financial_transactions (initiated_by);
