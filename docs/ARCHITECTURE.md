# Architecture

## 1. Big picture

```
 Browser (React + Vite, port 5173)
    │  HTTPS/JSON, Authorization: Bearer <JWT>, Idempotency-Key on money calls
    ▼
 Spring Boot API (port 8080)
    web (controllers, DTOs, validation)  →  service (business rules, @Transactional)
        →  repository (Spring Data JPA, parameterised queries)  →  PostgreSQL 17 (Flyway-owned schema)
    security: stateless JWT filter → DbJwtAuthenticationConverter (loads user + role from DB)
```

Package layout (`com.snovasys.ledgerline`):

| Package | Responsibility |
|---|---|
| `config` | `SecurityConfig` (filter chain, CORS, BCrypt), `AppProperties`, bootstrap admin, expired-token cleanup |
| `security` | `JwtService` (issue/validate), `DbJwtAuthenticationConverter`, `AuthUser`, 401/403 JSON handlers |
| `web` / `web.dto` | Controllers and request/response records. DTOs never expose the password hash |
| `service` | `AuthService`, `AccountService`, `TransactionService`, `IdempotencyService`, `AuditService`, `LedgerIntegrityService`, ... |
| `domain` | JPA entities and enums |
| `repository` | Spring Data repositories (incl. `SELECT ... FOR UPDATE` account locking) |
| `exception` | One error shape (`ApiError`), no stack traces or SQL in responses |

## 2. Authentication and authorization

1. `POST /api/auth/login` checks the BCrypt (cost 12) hash, applies the lockout rule (5 failures → 15 min), records an audit event, and returns an HS256 JWT (issuer `ledgerline`, 30 min expiry, unique `jti`).
2. On every request the token is verified (signature, algorithm pinned, issuer, expiry, not revoked). The **user, role and status are then reloaded from the database**. The role inside the token is never used, so a demoted/disabled user loses access immediately.
3. `SecurityConfig` maps URL prefixes to roles and ends with `anyRequest().denyAll()` (default deny):

| Prefix | Allowed role |
|---|---|
| `/api/health`, `/api/auth/register`, `/api/auth/login` | public |
| `/api/auth/logout`, `/api/users/me/**` | any authenticated user |
| `/api/accounts/**`, `/api/transactions/**` | `CUSTOMER` only |
| `/api/admin/**` | `ADMIN` only |
| `/api/auditor/**` | `AUDITOR` only |
| `/api/audit-events` | `ADMIN`, `AUDITOR` (read only, GET) |

4. **Ownership (IDOR defence)** is enforced in the service layer: `AccountService.requireOwned(caller, accountId)` looks the account up by id *and* the caller's user id taken from the authenticated principal. Someone else's account/transaction returns **404** (not 403) so ids can't be probed; the denial is written to the audit log.
5. Logout stores the token's `jti` in `revoked_tokens` (cleaned up after expiry). Changing the password invalidates older tokens.
6. CSRF protection is disabled on purpose: the API is stateless and reads the token from the `Authorization` header, never from a cookie. CORS allows only the configured explicit origins (startup fails on `*`).

## 3. Transfer algorithm (`TransactionService.execute`)

One `@Transactional` method; deposit, withdrawal and transfer all share it. The *source* is debited and the *destination* credited:

| Operation | Debit (source) | Credit (destination) |
|---|---|---|
| Deposit | system clearing account | customer account |
| Withdrawal | customer account | system clearing account |
| Transfer | sender account | receiver account |

Steps:

1. Controller checks the `Idempotency-Key` format; DTO validation checks amount (≥ 0.01, ≤ 2 decimals), currency, account-number shape.
2. Service verifies the source account belongs to the caller; resolves the destination (customer accounts only); rejects same-account transfers.
3. `IdempotencyService.claim(user, key, requestHash)` inserts into `idempotency_records` (unique on `user_id + key`). If the key already exists with the same fingerprint, the **original transaction is returned** (HTTP 200, `Idempotent-Replayed: true`). Same key with a different payload → 422.
4. Both accounts are locked with `SELECT ... FOR UPDATE` **in id order**, so opposite-direction transfers cannot deadlock and two requests cannot both spend the same balance.
5. Validation runs against the *locked* rows: currency match, both accounts ACTIVE, sufficient funds (system account exempt).
6. Balances are updated; one `financial_transactions` row and exactly one DEBIT + one CREDIT `ledger_entries` row are inserted (each stores `balance_after`).
7. The idempotency record is linked to the transaction and an audit event is written **in the same database transaction**.
8. At commit, a **deferred constraint trigger** re-checks that every transaction has exactly one DEBIT and one CREDIT of equal amount on the right accounts. If anything fails, the whole transaction rolls back and a `TRANSACTION_REJECTED` audit event is written in a separate transaction (so the rejection itself is not lost).

## 4. Data model (Flyway V1–V7)

| Migration | Table | Key constraints |
|---|---|---|
| V1 | `users` | unique lower-case email, role/status CHECKs, failed-login counters |
| V2 | `accounts` | unique `account_number`, `NUMERIC(19,2)`, `CHECK (balance >= 0)`, one SYSTEM account per currency, owner FK |
| V3 | `financial_transactions` | amount > 0, type/status CHECKs, source ≠ destination, unique reference |
| V4 | `ledger_entries` | FK to transaction + account, amount > 0, append-only triggers, deferred balance trigger |
| V5 | `idempotency_records` | `UNIQUE (user_id, idempotency_key)`, request hash, FK to transaction |
| V6 | `audit_events` | append-only triggers (UPDATE/DELETE/TRUNCATE rejected), indexes on time/actor/action |
| V7 | `revoked_tokens` | `jti` primary key, expiry index |

Money is always `NUMERIC(19,2)` / `BigDecimal`; no floating point anywhere. Hibernate runs with `ddl-auto: none` — Flyway owns the schema.

## 5. Audit trail

`AuditService.record` writes inside the caller's transaction (so a financial event commits or rolls back together with the money). `recordIndependent` uses `REQUIRES_NEW` for events that must survive a rollback (failed login, authorization denial, rejected transaction). Metadata keys that look like secrets (`password`, `token`, `secret`, ...) are dropped and long values truncated. No controller exposes any write/delete on audit data, and database triggers reject UPDATE/DELETE/TRUNCATE even from a direct SQL session.

## 6. Frontend

React 19 + Vite, plain JavaScript/CSS, a tiny built-in router. `lib/api.js` attaches the bearer token and an `Idempotency-Key` (generated once per form submission and reused on retry), turns errors into a single shape, and clears the session on 401. Token is kept in `sessionStorage` (not `localStorage`). `ProtectedRoute` and role-based navigation are **UX only**; the backend decides access. All user-supplied text is rendered through React (escaped); there is no `dangerouslySetInnerHTML`.
