# Testing

## Verified status (November 8, 2026)

The following checks were executed on the developer's local machine.

| Area | Check | Result |
|---|---|---|
| Backend | Maven test suite | **50 passed, 0 failures, 0 errors, 0 skipped** |
| Backend | Integration tests against real PostgreSQL 17 | **17 passed, 0 failures, 0 errors, 0 skipped** |
| Backend | Unit tests | **33 passed, 0 failures, 0 errors, 0 skipped** |
| Backend | Spring Boot application startup | **Started successfully; Tomcat on port 8080** |
| Backend | Health endpoint | **GET /api/health returned UP** |
| Frontend | Node.js unit tests | **20 passed, 0 failed** |
| Frontend | Vite production build | **Succeeded** |
| Database | SQL guarantees script | **28 checks passed on PostgreSQL 16** (separate earlier verification) |
| Browser | Manual customer, admin and auditor workflows against the running backend | **Verified manually** |
| Browser | Generation-time mock API smoke test | **34 of 34 checks passed** (not a real-backend E2E test) |

## Backend unit tests (33)

Source: `backend/src/test/java/.../`

| Test class | Tests | Coverage |
|---|---:|---|
| `security/JwtServiceTest` | 9 | Token issue/validation, expiry, issuer, tampering, algorithm rejection and secret validation |
| `service/AccountNumberGeneratorTest` | 3 | Account number format and uniqueness |
| `service/AuditServiceTest` | 6 | Sensitive metadata filtering, JSON escaping and value limits |
| `service/IdempotencyServiceTest` | 6 | Key format, fingerprints, replay and conflict behaviour |
| `service/MoneyUtilTest` | 5 | Money precision, positive amounts and limits |
| `service/PasswordPolicyTest` | 4 | Password length, character requirements and bcrypt byte limit |
| **Total** | **33** | **All passed** |

## Backend integration tests (17)

Source: `backend/src/test/java/.../integration/LedgerlineIntegrationTest`

Executed against a real PostgreSQL 17 database using Spring Boot `@SpringBootTest(RANDOM_PORT)`, HTTP requests and JDBC assertions.

**Result: 17 passed, 0 failures, 0 errors, 0 skipped.**

| Test area | Verified behaviour |
|---|---|
| Authentication | Registration, login, duplicate email rejection, role selection prevention, password hash protection, generic login errors and account lockout |
| JWT and sessions | Invalid JWT rejection and logout token revocation |
| Authorization | Backend role-based access restrictions |
| IDOR | Customer cannot access another customer's account or transactions |
| Financial operations | Deposit, withdrawal, transfer, double-entry ledger and balance updates |
| Validation | Invalid requests rejected without changing financial records |
| Account status | Frozen accounts cannot move money |
| Idempotency | Duplicate key replay returns original result and moves money once |
| Concurrent idempotency | Concurrent requests using the same key create only one transaction |
| Concurrent transfers | No overdraft and consistent balances |
| Deadlock prevention | Opposite-direction concurrent transfers complete without deadlock |
| Audit | Audit events are created, visible to auditors and contain no secrets |
| Database immutability | Audit, ledger and transaction tables reject unauthorized modifications |

## Frontend unit tests (20)

Source: `frontend/src/lib/*.test.js`

All 20 tests passed.

Coverage includes validators, password policy mirror, money parsing, idempotency-key generation and validation, sessionStorage token handling, logout cleanup and API client behaviour.

## Frontend production build

- React 19 and Vite 7.3.7.
- `npm run build` completed successfully.
- 50 modules transformed.
- Production output generated in `frontend/dist`.

## Manual browser verification

The frontend was manually checked against the running local backend.

- Customer login and dashboard.
- Account creation.
- Deposit, withdrawal and transfer.
- Transaction history and account filters.
- Admin login, user management and audit trail.
- Auditor login, financial records and audit events.
- Auditor ledger-integrity check.
- Auditor access to the admin UI was blocked/redirected.

These are manual checks, not automated browser E2E tests.

## Database SQL guarantees

`database/tests/db_guarantees.sql` contains 28 PASS/FAIL checks.

The documented result is 28 passed on PostgreSQL 16. These SQL checks are separate from the PostgreSQL 17 Spring Boot integration tests above.

## How to run

Backend unit tests:

```bash
cd backend
mvn test
```

Integration tests require the `LEDGERLINE_*` environment variables configured for a dedicated, disposable PostgreSQL test database. They create test users and financial records. Do not point them at a production or valuable database.

```bash
mvn test -Dtest=LedgerlineIntegrationTest
```

Frontend tests and build:

```bash
cd frontend
npm install
npm test
npm run build
```

Database SQL checks should be run against a scratch database:

```bash
psql -d <scratch_db> -f database/tests/db_guarantees.sql
```

## Not covered / remaining gaps

- No automated browser E2E suite against the real backend.
- No load or stress test beyond the concurrency integration tests.
- No automated dependency/CVE scan has been completed.
- No per-IP global rate limiting.
- No refresh-token mechanism.
- Frontend component tests are not included.
- Production deployment, TLS termination and external audit-log storage still require deployment configuration and operational work.
- The database SQL guarantees result recorded above is from PostgreSQL 16; the 17 integration tests were separately executed against PostgreSQL 17.
