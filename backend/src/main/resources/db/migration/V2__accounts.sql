-- V2: accounts (customer accounts + one system/clearing account per currency)
CREATE TABLE accounts (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number  VARCHAR(20)   NOT NULL,
    user_id         UUID          REFERENCES users (id),
    account_type    VARCHAR(10)   NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    balance         NUMERIC(19,2) NOT NULL DEFAULT 0,
    status          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_accounts_number   UNIQUE (account_number),
    CONSTRAINT chk_accounts_type    CHECK (account_type IN ('CUSTOMER', 'SYSTEM')),
    CONSTRAINT chk_accounts_status  CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_accounts_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    -- customer accounts have an owner, system accounts have none
    CONSTRAINT chk_accounts_owner   CHECK (
        (account_type = 'CUSTOMER' AND user_id IS NOT NULL) OR
        (account_type = 'SYSTEM'   AND user_id IS NULL)),
    -- a customer balance can never go negative, even if application code has a bug
    CONSTRAINT chk_accounts_balance CHECK (account_type = 'SYSTEM' OR balance >= 0)
);

CREATE INDEX idx_accounts_user ON accounts (user_id);

-- exactly one system (clearing) account per currency
CREATE UNIQUE INDEX uq_accounts_system_currency ON accounts (currency) WHERE account_type = 'SYSTEM';

INSERT INTO accounts (account_number, account_type, currency) VALUES
    ('SYS-CLEARING-INR', 'SYSTEM', 'INR'),
    ('SYS-CLEARING-USD', 'SYSTEM', 'USD'),
    ('SYS-CLEARING-EUR', 'SYSTEM', 'EUR');
