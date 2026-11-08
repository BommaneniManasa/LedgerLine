# Threat Model

Scope: LedgerLine API + React client + PostgreSQL. Assets: customer money (balances/ledger), user credentials and sessions, audit trail integrity.
Method: STRIDE-style walk through each entry point. "Verified" below refers to checks that were actually run (see [docs/TESTING.md](docs/TESTING.md)); anything else is *designed in but not yet run against the real backend*.

| # | Threat | Impact | Mitigations in the code | Verification status | Remaining risk |
|---|---|---|---|---|---|
| 1 | **Broken access control / IDOR** – customer reads or spends from someone else's account by changing an id | Theft, privacy breach | Ownership checked in service layer using the principal's user id (never an id from the request); other users' resources return 404; `AUTHZ_DENIED` audited; URL rules end in `denyAll()` | Integration tests passed: IDOR and ownership checks on PostgreSQL 17; SQL schema tested | A new endpoint that forgets `requireOwned` would be vulnerable – review checklist needed |
| 2 | **Privilege escalation** – register as ADMIN, edit own role, forge role claim | Full takeover | No `role` field at registration; only ADMIN can change roles and not their own; role is reloaded from DB on every request, token role ignored; profile update ignores role | Integration tests passed: role-based access and registration protections | First admin comes from env bootstrap – protect that password |
| 3 | **Password exposure** | Account takeover | BCrypt cost 12; hash never in any DTO; passwords never logged; audit metadata filter drops secret-like keys; generic login error (no user enumeration) | PasswordPolicyTest passed; integration tests verified password-hash protection | No breached-password check, no MFA |
| 4 | **Brute force / credential stuffing** | Account takeover | Lockout after 5 failures for 15 min; every attempt audited | Integration test passed: account lockout after repeated failed logins | No per-IP rate limit; attacker can lock victims out (DoS) |
| 5 | **JWT attacks** – `alg=none`, tampering, expired/replayed token, weak secret | Impersonation | HS256 pinned, issuer + expiry checked, secret ≥ 32 bytes enforced at startup (no default), `jti` revocation on logout and password change, short 30 min life | JwtServiceTest passed; integration tests verified tampered and alg=none token rejection | No refresh tokens; stolen token valid until expiry unless revoked; token in `sessionStorage` |
| 6 | **SQL injection** | Data theft/modification | Only Spring Data / JPA parameter binding; no string-built SQL; strict DTO validation (patterns for account numbers, enums) | Reviewed by reading | None known; re-check any new native query |
| 7 | **XSS** (stored via description/name) | Token theft, UI abuse | React escapes output, no `dangerouslySetInnerHTML`; API CSP `default-src 'none'`; input length limits | Browser smoke test rendered hostile text as plain text (mock API) | XSS in a future component would expose `sessionStorage` token; production frontend host needs its own CSP |
| 8 | **CSRF** | Forced actions | Not cookie-authenticated: token is read only from the `Authorization` header, sessions are stateless | Design | If cookies are ever introduced, CSRF protection must be re-enabled |
| 9 | **CORS misconfiguration** | Cross-origin data access | Explicit origin list; `*` rejected at startup; no credentials; limited headers/methods | Design | Wrong origin configured in production |
| 10 | **Duplicate transactions / replay / double click** | Money moved twice | Mandatory `Idempotency-Key`; `UNIQUE(user_id, key)`; request fingerprint; replay returns original; client reuses key on retry | DB uniqueness tested (SQL); concurrent same-key idempotency integration tests passed | Idempotency records are per user; keys are not purged yet |
| 11 | **Race conditions** – parallel withdrawals overdraw | Negative balance, lost money | `SELECT ... FOR UPDATE` on both accounts in id order; validation on locked rows; `CHECK (balance >= 0)` | `CHECK` tested (SQL); concurrent transfer integration tests passed: no overdraw and no deadlock | Row locks reduce throughput for hot accounts |
| 12 | **Ledger manipulation / incomplete ledger** | Books don't balance, hidden theft | One transaction for everything; deferred constraint trigger requires exactly one DEBIT + one CREDIT, equal amounts, right accounts; ledger and transaction tables reject UPDATE/DELETE/TRUNCATE; auditor integrity report recomputes totals | **28 SQL checks passed on PostgreSQL 16** | A DB superuser can disable triggers; corrections must be new reversing transactions (not implemented in the UI) |
| 13 | **Audit log tampering** | Evidence destroyed | No write/delete API; append-only triggers; app DB user should not own tables in production; reading the log is audited | SQL triggers and audit immutability integration tests passed on PostgreSQL 17 | Superuser can bypass; ship logs to external write-once storage |
| 14 | **Sensitive data exposure / verbose errors** | Information leak | Single `ApiError` shape; stack traces/messages disabled in Spring; DTO-only responses; secrets only via environment variables; `.env` git-ignored | Secret scan of the repo before zipping | Server logs must be protected; TLS must be provided by a reverse proxy |
| 15 | **Insider abuse by ADMIN** | Fraud | Admin cannot move money or edit own role/status; every admin action audited | Design | Admins can freeze accounts and create users; needs second-person review in real life |
| 16 | **Dependency vulnerabilities** | Various | Spring Boot BOM versions | **Not scanned** | Run `mvn dependency-check` and `npm audit` |
| 17 | **Denial of service** | Outage | Page size caps, request validation, amount caps | Design | No global rate limiting / request-size tuning |

## Assumptions
- TLS terminates in front of the app; the DB is not exposed publicly; secrets are supplied by the deployer.
- Deposits/withdrawals are simulated against a clearing account (no real payment network).

## Priority follow-ups
1. [x] Integration tests against PostgreSQL 17 — 17/17 passed (rows 1, 2, 10, 11 and 13 verified).
2. Add per-IP rate limiting and a refresh-token design.
3. Run dependency scans.
4. Use a separate low-privilege database role for the application.
