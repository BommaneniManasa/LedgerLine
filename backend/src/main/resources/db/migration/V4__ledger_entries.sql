-- V4: double-entry ledger (append-only)
CREATE TABLE ledger_entries (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID          NOT NULL REFERENCES financial_transactions (id),
    account_id      UUID          NOT NULL REFERENCES accounts (id),
    entry_type      VARCHAR(6)    NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    balance_after   NUMERIC(19,2) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_ledger_type     CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount   CHECK (amount > 0),
    CONSTRAINT chk_ledger_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    -- at most ONE debit and ONE credit per transaction
    CONSTRAINT uq_ledger_one_entry_per_type UNIQUE (transaction_id, entry_type)
);

CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at DESC);

-- Shared "append-only" guard, reused by audit_events (V6).
CREATE OR REPLACE FUNCTION prevent_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% on % is not allowed: this table is append-only', TG_OP, TG_TABLE_NAME
        USING ERRCODE = '42501';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_no_update_delete
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_modification();

CREATE TRIGGER trg_ledger_no_truncate
    BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_modification();

CREATE TRIGGER trg_txn_no_update_delete
    BEFORE UPDATE OR DELETE ON financial_transactions
    FOR EACH ROW EXECUTE FUNCTION prevent_modification();

CREATE TRIGGER trg_txn_no_truncate
    BEFORE TRUNCATE ON financial_transactions
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_modification();

-- Every COMPLETED transaction must have exactly one DEBIT and one CREDIT of equal amount,
-- on the correct accounts. Checked at COMMIT time (deferred), so the application can insert
-- the transaction first and the two entries afterwards, but can never commit an incomplete ledger.
CREATE OR REPLACE FUNCTION verify_transaction_balanced() RETURNS trigger AS $$
DECLARE
    entry_count  INTEGER;
    debit_total  NUMERIC(19,2);
    credit_total NUMERIC(19,2);
BEGIN
    IF NEW.status <> 'COMPLETED' THEN
        RETURN NULL;
    END IF;

    SELECT count(*),
           COALESCE(sum(amount) FILTER (WHERE entry_type = 'DEBIT'), 0),
           COALESCE(sum(amount) FILTER (WHERE entry_type = 'CREDIT'), 0)
      INTO entry_count, debit_total, credit_total
      FROM ledger_entries
     WHERE transaction_id = NEW.id;

    IF entry_count <> 2 OR debit_total <> credit_total OR debit_total <> NEW.amount THEN
        RAISE EXCEPTION 'Ledger imbalance for transaction %', NEW.id USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ledger_entries
                    WHERE transaction_id = NEW.id AND entry_type = 'DEBIT'
                      AND account_id = NEW.source_account_id)
       OR NOT EXISTS (SELECT 1 FROM ledger_entries
                       WHERE transaction_id = NEW.id AND entry_type = 'CREDIT'
                         AND account_id = NEW.destination_account_id) THEN
        RAISE EXCEPTION 'Ledger entries reference wrong accounts for transaction %', NEW.id
            USING ERRCODE = '23514';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_txn_must_be_balanced
    AFTER INSERT ON financial_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_transaction_balanced();
