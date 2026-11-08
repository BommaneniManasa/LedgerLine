# Testing

## Honest status (what actually ran during generation)

The generation environment had **no access to Maven Central or the npm registry** (outbound package downloads blocked),
so the Spring Boot code could not be compiled and Vite could not be run there. Everything below is labelled accordingly.

| Area | Check | Result |
|---|---|---|
| Database | All 7 Flyway migrations applied to an empty **PostgreSQL 16.15** database via `psql` (not PG 17, not via Flyway itself) | **Executed — applied cleanly** |
| Database | `database/tests/db_guarantees.sql` (28 checks: unique email, balance ≥ 0, one system account per currency, NUMERIC money, deferred ledger-balance trigger — missing/one-sided/unequal/wrong-account entries rejected, balanced commit OK, one DEBIT + one CREDIT only, ledger/transaction/audit UPDATE·DELETE·TRUNCATE blocked, idempotency uniqueness) | **Executed — 28 passed, 0 failed** |
| Frontend | `npm test` (`node --test`): validators, password policy, money parsing, idempotency keys, token storage, API client with mocked `fetch` | **Executed — 20 passed, 0 failed** (re-run independently) |
| Frontend | esbuild bundle of `src/main.jsx` with React 19 (proves JSX compiles) | **Executed — compiled** (this is *not* `vite build`) |
| Frontend | Playwright browser smoke test of the bundled UI against a **throw-away mock API** (login errors, protected-route redirects, role guards, deposit/withdraw/transfer flows incl. confirm step and rejection display, admin + auditor screens, register/logout, 404, hostile text rendered as plain text, token never in `localStorage`, 390 px layout) | **Executed — 34 of 34 checks passed.** Run by a sub-agent during generation; the mock server/script are not part of the ZIP. Not run against the real backend |
| Backend | Java syntax parse of all 80 main + test files (`javac` parser) | **Executed — 0 syntax errors.** This is a parse only, **not a compile** |
| Backend | `mvn compile`, `mvn test` (33 unit tests), `mvn spring-boot:run` | **NOT executed** (no dependencies available) |
| Backend | `LedgerlineIntegrationTest` (18 tests, real HTTP + PostgreSQL) | **NOT executed** (needs Spring + DB) |
| Frontend | `npm install`, `npm run build` (Vite) | **NOT executed** (no registry access) |
| System | Real frontend ↔ real backend ↔ PostgreSQL 17 end-to-end | **NOT executed** |

Please treat the NOT-executed rows as the first thing to run on your machine (see the README checklist).

## Test inventory

### Backend unit tests (33 `@Test`, no DB) — `backend/src/test/java/.../`
| File | Covers |
|---|---|
| `security/JwtServiceTest` | token issue/validate, expiry, wrong issuer, tampered signature, `alg=none`, weak/missing secret |
| `service/PasswordPolicyTest` | length, letter+digit, 72-byte bcrypt limit |
| `service/MoneyUtilTest` | 2-decimal normalisation, rejection of ≤ 0, > 2 decimals, over-limit |
| `service/AccountNumberGeneratorTest` | format and uniqueness of generated numbers |
| `service/IdempotencyServiceTest` | key format, fingerprint stability/difference, replay vs conflict vs reuse logic |
| `service/AuditServiceTest` | secret-looking metadata keys dropped, JSON escaping, value length limits |

### Backend integration tests (18 `@Test`) — `integration/LedgerlineIntegrationTest`
Real HTTP (JDK `HttpClient`) against `@SpringBootTest(RANDOM_PORT)` + real PostgreSQL + JDBC assertions.
Skipped automatically unless `LEDGERLINE_DB_URL` and `LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL` are set.
**Use an empty scratch database** — tests create users and move test money.

| Area | Tests |
|---|---|
| Authentication | register/login + duplicate email · role cannot be chosen at registration, no password in responses · generic error for wrong password vs unknown user · lockout after 5 failures · missing/garbage/tampered/`alg=none` JWT → 401 · logout revokes token |
| Authorization | customer blocked from admin/auditor/audit endpoints · self-promotion blocked · admin/auditor blocked from customer money endpoints · auditor read-only (writes 403, DELETE 403/405) · **IDOR**: other customer's account/ledger/transaction → 404, can't deposit/transfer out of it |
| Financial | deposit/withdraw/transfer balances · exactly 1 DEBIT + 1 CREDIT with equal amounts (JDBC) · integrity report balanced · insufficient funds, negative/zero/3-decimal amounts, same account, unknown destination, bad/missing idempotency key, wrong currency → rejected **and nothing changes** (rollback: balances, transaction count, ledger count unchanged) · frozen account blocked |
| Idempotency | retry returns original (200 + `Idempotent-Replayed`), money moves once · same key different payload rejected · 8 concurrent same-key requests → exactly 1 created, 7 replays |
| Concurrency | 12 parallel 10.00 transfers from a 50.00 account → exactly 5 succeed, balance 0.00, no overdraft, ledger balanced · 20 opposite-direction parallel transfers → no deadlock, balances restored |
| Audit | LOGIN_SUCCESS/FAILURE, USER_REGISTERED, ACCOUNT_CREATED, DEPOSIT events exist · no password in audit data · customers can't read audit · UPDATE/DELETE/TRUNCATE on audit/ledger/transaction tables fail with SQLSTATE 42501 even via direct JDBC |

### Frontend unit tests (20) — `frontend/src/lib/*.test.js`
Validators + password policy mirror, money parsing, idempotency-key generation/validation, token storage (sessionStorage only, cleared on logout), API client (bearer header, 401 clears session, error shape).

### Database tests — `database/tests/db_guarantees.sql`
28 PASS/FAIL checks, run with `psql` against a scratch DB (see table above).

## How to run
See README §5. Integration-test environment variables are the same `LEDGERLINE_*` variables the app uses, pointed at a scratch DB.

## Not covered (gaps)
- No browser E2E against the real backend, no load test, no automated dependency/CVE scan (run `mvn dependency-check` / `npm audit` yourself).
- Frontend component tests are not included; UI behaviour was only smoke-tested against a mock.
