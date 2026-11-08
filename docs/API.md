# API Reference

Base URL: `http://localhost:8080`. All bodies are JSON. Authenticated calls need `Authorization: Bearer <accessToken>`.
Money calls (`deposit`, `withdraw`, `transfer`) also require `Idempotency-Key: <8–64 chars, letters/digits/-/_>` (a UUID is recommended).

## Error shape (every error)

```json
{ "timestamp": "...", "status": 422, "code": "INSUFFICIENT_FUNDS", "message": "Insufficient funds",
  "path": "/api/transactions/withdraw", "fieldErrors": null }
```
`fieldErrors` is filled for validation errors (`400`). Typical statuses: `400` validation, `401` missing/invalid/revoked token or bad credentials, `403` wrong role, `404` not found **or not yours**, `409` conflict (duplicate email, idempotency conflict), `422` business rule (insufficient funds, currency mismatch, inactive account...). Messages never contain stack traces, SQL or class names.

## Auth (public unless noted)

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | `email, password (10–72 chars, letter+digit), fullName, phone?` | Always creates a `CUSTOMER`. No `role` field exists. Duplicate email → 409 |
| POST | `/api/auth/login` | `email, password` | → `{accessToken, tokenType, expiresAt, user}`. Same generic 401 for unknown user and wrong password. 5 failures lock the user for 15 min |
| POST | `/api/auth/logout` | – | **Authenticated.** Revokes the token (`jti`) |
| GET | `/api/health` | – | `{"status":"UP"}` |

## Profile (any authenticated role)

| Method | Path | Body |
|---|---|---|
| GET | `/api/users/me` | – |
| PUT | `/api/users/me` | `fullName, phone?` (email and role cannot be changed here) |
| POST | `/api/users/me/password` | `currentPassword, newPassword` (invalidates older tokens) |

## Customer — accounts (role `CUSTOMER`, own data only)

| Method | Path | Body / notes |
|---|---|---|
| GET | `/api/accounts` | the caller's accounts |
| POST | `/api/accounts` | `{currency}` (INR, USD, EUR; max 5 accounts per user) |
| GET | `/api/accounts/{id}` | 404 if not owned |
| GET | `/api/accounts/{id}/ledger?page&size` | ledger entries of an owned account |

## Customer — transactions (role `CUSTOMER`)

| Method | Path | Body |
|---|---|---|
| POST | `/api/transactions/deposit` | `accountId, amount, currency, description?` |
| POST | `/api/transactions/withdraw` | `accountId, amount, currency, description?` |
| POST | `/api/transactions/transfer` | `sourceAccountId, destinationAccountNumber, amount, currency, description?` |
| GET | `/api/transactions?accountId&page&size` | history of the caller's accounts only |
| GET | `/api/transactions/{id}` | 404 unless the caller owns the source or destination |

Response body: the transaction `{id, reference, type, status, amount, currency, description, sourceAccountNumber, destinationAccountNumber, direction, createdAt}`.
Created → `201`. A retry with the same key and same payload → `200` with header `Idempotent-Replayed: true` and no second movement of money. Same key with a different payload → `422 IDEMPOTENCY_KEY_REUSED`. Same key while the first request is still running → `409 IDEMPOTENCY_IN_PROGRESS`. Malformed key → `400 INVALID_IDEMPOTENCY_KEY`.
`amount` is a decimal with at most 2 fraction digits, ≥ 0.01 and ≤ 1,000,000.00.

## Admin (role `ADMIN`)

Admins manage users and accounts; they **cannot move money**, and cannot change their own role or status.

| Method | Path | Body |
|---|---|---|
| GET | `/api/admin/users?page&size` | – |
| POST | `/api/admin/users` | `email, password, fullName, role` (the only way to create ADMIN/AUDITOR users) |
| PATCH | `/api/admin/users/{id}/status` | `{status}` (ACTIVE / SUSPENDED / DISABLED) |
| PATCH | `/api/admin/users/{id}/role` | `{role}` |
| POST | `/api/admin/users/{id}/accounts` | `{currency}` – open an account for a customer |
| GET | `/api/admin/accounts?page&size` | all accounts incl. system accounts |
| PATCH | `/api/admin/accounts/{id}/status` | `{status}` (ACTIVE / FROZEN / CLOSED) |

## Auditor (role `AUDITOR`, read only)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/auditor/accounts` | all accounts |
| GET | `/api/auditor/transactions` | all transactions |
| GET | `/api/auditor/transactions/{id}/ledger` | the debit and credit entries |
| GET | `/api/auditor/ledger-integrity` | recomputes: every transaction has 1 DEBIT + 1 CREDIT of equal amount, stored balances equal the ledger → `{balanced, totalDebits, totalCredits, unbalancedTransactions, mismatchedAccounts}` |

## Audit events (roles `ADMIN`, `AUDITOR`, GET only)

`GET /api/audit-events?action=<ACTION>&page&size` → `{actorEmail, actorRole, action, outcome, targetType, targetId, ipAddress, metadata, occurredAt}`.
Reading the log is itself audited (`AUDIT_LOG_VIEWED`). There is no POST/PUT/PATCH/DELETE for audit data.

Actions: `LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, USER_REGISTERED, USER_CREATED_BY_ADMIN, ADMIN_BOOTSTRAPPED, PROFILE_UPDATED, PASSWORD_CHANGED, USER_STATUS_CHANGED, USER_ROLE_CHANGED, ACCOUNT_CREATED, ACCOUNT_STATUS_CHANGED, DEPOSIT, WITHDRAWAL, TRANSFER, TRANSACTION_REJECTED, AUTHZ_DENIED, AUDIT_LOG_VIEWED`.

## curl example (idempotent retry)

```bash
KEY=$(uuidgen)
curl -s -X POST http://localhost:8080/api/transactions/deposit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d '{"accountId":"<uuid>","amount":"100.00","currency":"INR"}'
# run it again with the same $KEY: HTTP 200 + Idempotent-Replayed: true, balance unchanged
```

> Exact HTTP status codes for individual error cases are defined in `GlobalExceptionHandler` and the exception classes; the integration tests assert them. This file was written from the source, not from a running server.
