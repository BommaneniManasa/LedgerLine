-- LedgerLine database-level guarantees test.
-- Run AFTER the Flyway migrations have been applied to an EMPTY database:
--   psql -d ledgerline -v ON_ERROR_STOP=0 -f database/tests/db_guarantees.sql
-- Every check prints PASS or FAIL. The script leaves test rows behind: use a scratch database.
\set QUIET on
\pset tuples_only on

CREATE TEMP TABLE results (name text, passed boolean);

-- helper: run a statement, record whether it raised an error of the expected SQLSTATE
CREATE OR REPLACE FUNCTION pg_temp.expect_error(test_name text, stmt text, expected_state text) RETURNS void AS $$
BEGIN
    BEGIN
        EXECUTE stmt;
        INSERT INTO results VALUES (test_name, false);
    EXCEPTION WHEN OTHERS THEN
        INSERT INTO results VALUES (test_name, SQLSTATE = expected_state);
    END;
END; $$ LANGUAGE plpgsql;

-- fixtures
INSERT INTO users (id, email, password_hash, full_name, role) VALUES
 ('00000000-0000-0000-0000-000000000001','a@test.io','x','A','CUSTOMER'),
 ('00000000-0000-0000-0000-000000000002','b@test.io','x','B','CUSTOMER');
INSERT INTO accounts (id, account_number, user_id, account_type, currency, balance) VALUES
 ('10000000-0000-0000-0000-000000000001','LL-TEST-A', '00000000-0000-0000-0000-000000000001','CUSTOMER','INR',100),
 ('10000000-0000-0000-0000-000000000002','LL-TEST-B', '00000000-0000-0000-0000-000000000002','CUSTOMER','INR',0);

-- 1 duplicate email rejected (unique index)  23505
SELECT pg_temp.expect_error('duplicate email rejected',
  $$INSERT INTO users (email,password_hash,full_name,role) VALUES ('a@test.io','x','Dup','CUSTOMER')$$, '23505');
-- 2 uppercase email rejected (check)  23514
SELECT pg_temp.expect_error('non-lowercase email rejected',
  $$INSERT INTO users (email,password_hash,full_name,role) VALUES ('UP@test.io','x','Up','CUSTOMER')$$, '23514');
-- 3 invalid role rejected
SELECT pg_temp.expect_error('invalid role rejected',
  $$INSERT INTO users (email,password_hash,full_name,role) VALUES ('r@test.io','x','R','SUPERUSER')$$, '23514');
-- 4 negative customer balance rejected
SELECT pg_temp.expect_error('negative customer balance rejected',
  $$UPDATE accounts SET balance = -1 WHERE account_number = 'LL-TEST-A'$$, '23514');
-- 5 duplicate account number rejected
SELECT pg_temp.expect_error('duplicate account number rejected',
  $$INSERT INTO accounts (account_number,user_id,account_type,currency) VALUES ('LL-TEST-A','00000000-0000-0000-0000-000000000001','CUSTOMER','INR')$$, '23505');
-- 6 second system account for same currency rejected
SELECT pg_temp.expect_error('only one system account per currency',
  $$INSERT INTO accounts (account_number,account_type,currency) VALUES ('SYS-2','SYSTEM','INR')$$, '23505');
-- 7 customer account without owner rejected
SELECT pg_temp.expect_error('customer account needs owner',
  $$INSERT INTO accounts (account_number,account_type,currency) VALUES ('LL-NOOWN','CUSTOMER','INR')$$, '23514');
-- 8 floating/precision: money columns are NUMERIC(19,2)
INSERT INTO results SELECT 'money columns are NUMERIC', count(*) = 4 FROM information_schema.columns
  WHERE table_name IN ('accounts','financial_transactions','ledger_entries') AND column_name IN ('balance','amount','balance_after') AND data_type = 'numeric';

-- 9 zero / negative amount rejected
SELECT pg_temp.expect_error('zero amount rejected',
  $$INSERT INTO financial_transactions (reference,type,status,source_account_id,destination_account_id,amount,currency,initiated_by)
    VALUES ('R0','TRANSFER','COMPLETED','10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002',0,'INR','00000000-0000-0000-0000-000000000001')$$, '23514');
-- 10 self transfer rejected
SELECT pg_temp.expect_error('same source and destination rejected',
  $$INSERT INTO financial_transactions (reference,type,status,source_account_id,destination_account_id,amount,currency,initiated_by)
    VALUES ('R1','TRANSFER','COMPLETED','10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',5,'INR','00000000-0000-0000-0000-000000000001')$$, '23514');

-- 11 COMPLETED transaction with NO ledger entries cannot commit (deferred constraint trigger)
DO $$ BEGIN
  BEGIN
    INSERT INTO financial_transactions (id,reference,type,status,source_account_id,destination_account_id,amount,currency,initiated_by)
    VALUES ('20000000-0000-0000-0000-000000000001','R2','TRANSFER','COMPLETED','10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002',10,'INR','00000000-0000-0000-0000-000000000001');
    SET CONSTRAINTS ALL IMMEDIATE;
    INSERT INTO results VALUES ('transaction without ledger entries cannot commit', false);
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO results VALUES ('transaction without ledger entries cannot commit', SQLSTATE = '23514');
  END;
END $$;

-- 12 transaction with only a DEBIT entry cannot commit
DO $$ BEGIN
  BEGIN
    INSERT INTO financial_transactions (id,reference,type,status,source_account_id,destination_account_id,amount,currency,initiated_by)
    VALUES ('20000000-0000-0000-0000-000000000002','R3','TRANSFER','COMPLETED','10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002',10,'INR','00000000-0000-0000-0000-000000000001');
    INSERT INTO ledger_entries (transaction_id,account_id,entry_type,amount,currency,balance_after)
    VALUES ('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','DEBIT',10,'INR',90);
    SET CONSTRAINTS ALL IMMEDIATE;
    INSERT INTO results VALUES ('one-sided ledger cannot commit', false);
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO results VALUES ('one-sided ledger cannot commit', SQLSTATE = '23514');
  END;
END $$;

-- 13 entries with unequal amounts cannot commit
DO $$ BEGIN
  BEGIN
    INSERT INTO financial_transactions (id,reference,type,status,source_account_id,destination_account_id,amount,currency,initiated_by)
    VALUES ('20000000-0000-0000-0000-000000000003','R4','TRANSFER','COMPLETED','10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002',10,'INR','00000000-0000-0000-0000-000000000001');
    INSERT INTO ledger_entries (transaction_id,account_id,entry_type,amount,currency,balance_after) VALUES
     ('20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000001','DEBIT',10,'INR',90),
     ('20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000002','CREDIT',9,'INR',9);
    SET CONSTRAINTS ALL IMMEDIATE;
    INSERT INTO results VALUES ('unequal debit/credit cannot commit', false);
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO results VALUES ('unequal debit/credit cannot commit', SQLSTATE = '23514');
  END;
END $$;

-- 14 entries on the WRONG accounts cannot commit
DO $$ BEGIN
  BEGIN
    INSERT INTO financial_transactions (id,reference,type,status,source_account_id,destination_account_id,amount,currency,initiated_by)
    VALUES ('20000000-0000-0000-0000-000000000004','R5','TRANSFER','COMPLETED','10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002',10,'INR','00000000-0000-0000-0000-000000000001');
    INSERT INTO ledger_entries (transaction_id,account_id,entry_type,amount,currency,balance_after) VALUES
     ('20000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002','DEBIT',10,'INR',0),
     ('20000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000001','CREDIT',10,'INR',110);
    SET CONSTRAINTS ALL IMMEDIATE;
    INSERT INTO results VALUES ('ledger entries on wrong accounts cannot commit', false);
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO results VALUES ('ledger entries on wrong accounts cannot commit', SQLSTATE = '23514');
  END;
END $$;

-- 15 a correct transaction commits (this one is real and stays)
BEGIN;
INSERT INTO financial_transactions (id,reference,type,status,source_account_id,destination_account_id,amount,currency,initiated_by)
VALUES ('20000000-0000-0000-0000-000000000009','R-OK','TRANSFER','COMPLETED','10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002',10,'INR','00000000-0000-0000-0000-000000000001');
INSERT INTO ledger_entries (transaction_id,account_id,entry_type,amount,currency,balance_after) VALUES
 ('20000000-0000-0000-0000-000000000009','10000000-0000-0000-0000-000000000001','DEBIT',10,'INR',90),
 ('20000000-0000-0000-0000-000000000009','10000000-0000-0000-0000-000000000002','CREDIT',10,'INR',10);
COMMIT;
INSERT INTO results SELECT 'balanced transaction commits', count(*) = 2 FROM ledger_entries WHERE transaction_id = '20000000-0000-0000-0000-000000000009';

-- 16 a third entry for the same type is rejected (one debit + one credit max)
SELECT pg_temp.expect_error('second DEBIT entry rejected',
  $$INSERT INTO ledger_entries (transaction_id,account_id,entry_type,amount,currency,balance_after)
    VALUES ('20000000-0000-0000-0000-000000000009','10000000-0000-0000-0000-000000000001','DEBIT',1,'INR',0)$$, '23505');

-- 17-20 ledger / transaction are append-only
SELECT pg_temp.expect_error('ledger UPDATE blocked',   $$UPDATE ledger_entries SET amount = 999$$, '42501');
SELECT pg_temp.expect_error('ledger DELETE blocked',   $$DELETE FROM ledger_entries$$, '42501');
SELECT pg_temp.expect_error('ledger TRUNCATE blocked', $$TRUNCATE ledger_entries CASCADE$$, '42501');
SELECT pg_temp.expect_error('transaction UPDATE blocked', $$UPDATE financial_transactions SET amount = 999$$, '42501');
SELECT pg_temp.expect_error('transaction DELETE blocked', $$DELETE FROM financial_transactions$$, '42501');

-- 21-24 audit table is append-only
INSERT INTO audit_events (action, outcome) VALUES ('LOGIN_SUCCESS','SUCCESS');
INSERT INTO results SELECT 'audit INSERT allowed', count(*) = 1 FROM audit_events;
SELECT pg_temp.expect_error('audit UPDATE blocked',   $$UPDATE audit_events SET action = 'X'$$, '42501');
SELECT pg_temp.expect_error('audit DELETE blocked',   $$DELETE FROM audit_events$$, '42501');
SELECT pg_temp.expect_error('audit TRUNCATE blocked', $$TRUNCATE audit_events$$, '42501');

-- 25-26 idempotency uniqueness
INSERT INTO idempotency_records (user_id, idempotency_key, request_hash) VALUES ('00000000-0000-0000-0000-000000000001','key-12345678','h');
SELECT pg_temp.expect_error('duplicate idempotency key per user rejected',
  $$INSERT INTO idempotency_records (user_id, idempotency_key, request_hash) VALUES ('00000000-0000-0000-0000-000000000001','key-12345678','h')$$, '23505');
INSERT INTO idempotency_records (user_id, idempotency_key, request_hash) VALUES ('00000000-0000-0000-0000-000000000002','key-12345678','h');
INSERT INTO results VALUES ('same key allowed for a different user', true);

-- 27 seeded clearing accounts exist
INSERT INTO results SELECT 'system clearing accounts seeded', count(*) = 3 FROM accounts WHERE account_type = 'SYSTEM';

\pset tuples_only off
SELECT CASE WHEN passed THEN 'PASS' ELSE 'FAIL' END AS result, name FROM results;
SELECT count(*) FILTER (WHERE passed) AS passed, count(*) FILTER (WHERE NOT passed) AS failed FROM results;
