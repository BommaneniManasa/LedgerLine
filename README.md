# LedgerLine â€” Money Movement with a Ledger You Can Prove

A secure full-stack financial transaction app (Snovasys Fresher Hiring Challenge 2026):
customers hold accounts and move money; every movement is recorded as a balanced **double-entry ledger**,
is **idempotent**, **atomic**, and leaves an **append-only audit trail**.

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.0.x, Maven, Spring Security (stateless JWT, HS256), Spring Data JPA/Hibernate |
| Database | PostgreSQL 17, Flyway migrations (V1â€“V7) |
| Frontend | React 19, Vite, plain JavaScript/CSS (no UI framework, tiny built-in router) |

> **Verified status (November 8, 2026):** The backend was compiled, tested and started successfully on the developer's machine.
> Backend Maven tests: **50 passed** (17 integration tests against PostgreSQL 17 and 33 unit tests).
> Frontend tests: **20 passed**; Vite production build succeeded. Customer, admin and auditor workflows were also manually checked against the running backend.
> The 28 database SQL guarantee checks were previously executed on PostgreSQL 16. These are separate from the PostgreSQL 17 integration tests.
> See [docs/TESTING.md](docs/TESTING.md) for detailed results and remaining gaps.


## Features

- **Auth:** register (always CUSTOMER), login, BCrypt (cost 12), JWT with issuer/expiry/algorithm pinned, logout that revokes the token id, change password (invalidates older tokens), login lockout after 5 failures, no user enumeration.
- **RBAC (server-side, default deny):** `CUSTOMER` own accounts/transactions only Â· `ADMIN` user + account management (cannot move money, cannot change own role/status) Â· `AUDITOR` read-only financial + audit data and a ledger-integrity report. The role is read from the database on every request, never from the token or the frontend.
- **Accounts:** unique generated account numbers, multi-currency (INR/USD/EUR), status (ACTIVE/FROZEN/CLOSED), separate system clearing account per currency.
- **Transactions:** deposit, withdrawal, transfer, history, status; positive 2-decimal `NUMERIC(19,2)` money, ownership/status/currency/funds checks.
- **Double-entry ledger:** exactly one DEBIT + one CREDIT per transaction, equal amount, correct accounts â€” enforced by a **deferred database constraint trigger**, so an incomplete ledger can never commit. Ledger/transaction tables are append-only.
- **Idempotency:** mandatory `Idempotency-Key` header; unique `(user_id, key)` constraint; retries return the original result; same key + different payload is rejected; concurrent duplicates create one transaction.
- **Concurrency:** accounts locked with `SELECT â€¦ FOR UPDATE` in id order (no deadlocks, no overdraft); DB `CHECK (balance >= 0)` as a last line of defence.
- **Audit trail:** logins, registrations, account creation, deposits/withdrawals/transfers, admin actions, authorization failures, rejected transactions. Actor, action, target, time, IP, safe metadata. Append-only (DB triggers + no write API). Secrets are never logged.
- **Frontend:** login, register, customer dashboard, accounts + ledger, history, deposit, withdraw, transfer (with confirm step), profile, admin dashboard, auditor dashboard, protected routes, role-based nav, responsive layout.

## Prerequisites

- JDK 21, Maven 3.9+ (or use your IDE's Maven)
- Node.js 20+ and npm
- PostgreSQL 17 (local install **or** Docker: `docker compose --env-file .env up -d`)
- `psql` (to create the database) â€” optional if you use Docker

## 1. Environment variables

Copy [`.env.example`](.env.example) to a real env file that stays out of Git and fill in **your own** values.

| Variable | Required | Meaning |
|---|---|---|
| `LEDGERLINE_DB_URL` | yes | e.g. `jdbc:postgresql://localhost:5432/ledgerline` |
| `LEDGERLINE_DB_USER` | yes | database user |
| `LEDGERLINE_DB_PASSWORD` | yes | database password |
| `LEDGERLINE_JWT_SECRET` | yes | random, â‰¥ 32 bytes (`openssl rand -base64 48`). App refuses to start if missing/weak |
| `LEDGERLINE_CORS_ALLOWED_ORIGINS` | no | default `http://localhost:5173`; explicit origins only, `*` is rejected |
| `LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` | no | creates the first ADMIN if the email doesn't exist. There is **no default admin** |
| `VITE_API_BASE_URL` (frontend) | no | default `/api` (Vite dev server proxies to `localhost:8080`) |

The app has **no defaults** for DB credentials or the JWT secret.

## 2. Database

```bash
# Option A - Docker (dev only)
cp .env.example .env            # then edit the password
docker compose --env-file .env up -d

# Option B - local PostgreSQL 17: edit the password placeholder in the file first
psql -U postgres -f database/create_database.sql
```

Tables are created automatically by **Flyway** when the backend starts (`backend/src/main/resources/db/migration/V1â€¦V7`).
To check the migrations/constraints independently on an empty scratch DB:
`psql -d <scratch_db> -f database/tests/db_guarantees.sql` (prints PASS/FAIL per check).

## 3. Run the backend

macOS/Linux:
```bash
cd backend
export LEDGERLINE_DB_URL=jdbc:postgresql://localhost:5432/ledgerline
export LEDGERLINE_DB_USER=ledgerline_app
export LEDGERLINE_DB_PASSWORD='...'
export LEDGERLINE_JWT_SECRET="$(openssl rand -base64 48)"
export LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
export LEDGERLINE_BOOTSTRAP_ADMIN_PASSWORD='...'
mvn spring-boot:run
```
Windows PowerShell:
```powershell
cd backend
$env:LEDGERLINE_DB_URL="jdbc:postgresql://localhost:5432/ledgerline"
$env:LEDGERLINE_DB_USER="ledgerline_app"
$env:LEDGERLINE_DB_PASSWORD="..."
$env:LEDGERLINE_JWT_SECRET="<48+ random characters>"
$env:LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL="admin@example.com"
$env:LEDGERLINE_BOOTSTRAP_ADMIN_PASSWORD="..."
mvn spring-boot:run
```
Health check: `http://localhost:8080/api/health`.

## 4. Run the frontend

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
npm run build        # production bundle in frontend/dist
```
Log in with the bootstrap admin to create an AUDITOR (Admin â†’ Create user). Customers self-register.

## 5. Tests

```bash
# backend unit tests (no database needed)
cd backend && mvn test

# backend integration tests: REAL HTTP + REAL PostgreSQL. Use an EMPTY throw-away database!
# (set the same LEDGERLINE_* variables as above, pointing at the scratch DB; without them these tests are skipped)
cd backend && mvn test -Dtest=LedgerlineIntegrationTest

# frontend unit tests
cd frontend && npm test

# database guarantees (SQL)
psql -d <scratch_db> -f database/tests/db_guarantees.sql
```
Details and honest results: [docs/TESTING.md](docs/TESTING.md).

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) â€” layers, request flow, transfer algorithm, data model
- [docs/API.md](docs/API.md) â€” every endpoint, roles, errors
- [docs/TESTING.md](docs/TESTING.md) â€” test inventory + what was / was not executed
- [THREATS.md](THREATS.md) â€” threat model, mitigations, residual risks
- [PROMPTS.md](PROMPTS.md) â€” AI usage log

## Known limitations

- **Backend verification:** Spring Boot 4.0.0 compiled and started successfully locally. Backend tests passed; see [docs/TESTING.md](docs/TESTING.md) for exact results.
- HTTPS/TLS is not terminated by the app: put it behind a TLS reverse proxy in production and set the real frontend origin in `LEDGERLINE_CORS_ALLOWED_ORIGINS`.
- No per-IP rate limiting (only per-account lockout) and no MFA / password reset by email.
- Access token lives 30 minutes, no refresh token; it is kept in `sessionStorage`, so an XSS bug would expose it (mitigated by React escaping, no `dangerouslySetInnerHTML`, strict API CSP).
- Deposit/withdrawal are simulated against the system clearing account (no real payment rails).
- Ledger-integrity totals add raw amounts across currencies, so they're a debit = credit check, not a financial total.
- Audit rows are tamper-*resistant* (triggers) but a database superuser can still disable triggers; ship audit logs to external write-once storage for stronger guarantees.
- `X-Forwarded-For` is deliberately not trusted for audit IPs; behind a proxy the IP will be the proxy's.

## Local verification checklist

- [x] PostgreSQL 17 configured and backend connected to the dedicated test database.
- [x] Backend Maven test suite: 50 passed, 0 failures.
- [x] Integration tests against PostgreSQL 17: 17 passed.
- [x] Spring Boot started successfully; health endpoint returned UP.
- [x] Frontend unit tests: 20 passed.
- [x] Vite production build succeeded.
- [x] Customer account creation, deposit, withdrawal, transfer and transaction history manually checked.
- [x] Admin user management and audit trail manually checked.
- [x] Auditor dashboard, financial records and ledger-integrity check manually checked.
- [x] Auditor UI access to admin page blocked/redirected.
- [x] Backend authorization, IDOR, idempotency and concurrency covered by passing integration tests.
- [ ] Dependency vulnerability scans.
- [ ] Per-IP rate limiting and refresh-token design.
- [ ] Production deployment and live URL.
- [ ] Demo video and final challenge submission.
- [ ] Final PROMPTS.md review and GitHub commit.
