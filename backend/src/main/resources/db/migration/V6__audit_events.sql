-- V6: audit events (append-only)
-- No foreign keys on purpose: audit rows must survive and must never block other changes.
CREATE TABLE audit_events (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_user_id  UUID,
    actor_email    VARCHAR(254),
    actor_role     VARCHAR(20),
    action         VARCHAR(50)  NOT NULL,
    outcome        VARCHAR(10)  NOT NULL,
    target_type    VARCHAR(30),
    target_id      VARCHAR(64),
    ip_address     VARCHAR(45),
    metadata       TEXT,
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX idx_audit_occurred ON audit_events (occurred_at DESC);
CREATE INDEX idx_audit_actor    ON audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_action   ON audit_events (action, occurred_at DESC);

CREATE TRIGGER trg_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_modification();

CREATE TRIGGER trg_audit_no_truncate
    BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_modification();
