-- V1: users
CREATE TABLE users (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(254) NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    full_name           VARCHAR(100) NOT NULL,
    phone               VARCHAR(20),
    role                VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_count  INTEGER      NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    token_valid_after   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role         CHECK (role IN ('ADMIN', 'CUSTOMER', 'AUDITOR')),
    CONSTRAINT chk_users_status       CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT chk_users_email_lower  CHECK (email = lower(email)),
    CONSTRAINT chk_users_failed_count CHECK (failed_login_count >= 0)
);

-- Duplicate-email prevention is enforced by the database, not only by application code.
CREATE UNIQUE INDEX uq_users_email ON users (email);
