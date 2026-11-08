// The ONLY place that talks to the backend. Every call:
//  - sends the JWT as "Authorization: Bearer ..." (never in the URL),
//  - never sends credentials/cookies (credentials: 'omit'): the API is stateless,
//  - turns every failure into an ApiError with the backend's code/message/fieldErrors.
// Roles and user ids are NEVER sent: the server derives them from the token.
import { clearToken, getToken } from './tokenStore.js';

export const API_BASE = (import.meta.env && import.meta.env.VITE_API_BASE_URL) || '/api';

export class ApiError extends Error {
  constructor(status, code, message, fieldErrors = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

let unauthorizedHandler = null;
/** AuthContext registers a function here so any 401 on an authenticated call logs the user out. */
export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn;
}

export function buildQuery(params = {}) {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`);
  return parts.length ? `?${parts.join('&')}` : '';
}

/** Low level call. Returns {data, status, replayed}. */
export async function requestRaw(path, { method = 'GET', body, auth = true, idempotencyKey, signal } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  if (auth) {
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(API_BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      credentials: 'omit',
      cache: 'no-store',
      signal,
    });
  } catch (e) {
    if (e && e.name === 'AbortError') throw e;
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your connection and try again.');
  }

  let data = null;
  if (response.status !== 204) {
    const text = await response.text();
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        data = null;
      }
    }
  }

  if (!response.ok) {
    if (response.status === 401 && auth) {
      clearToken();
      if (unauthorizedHandler) unauthorizedHandler();
    }
    throw new ApiError(
      response.status,
      (data && data.code) || 'REQUEST_ERROR',
      (data && data.message) || `Request failed (${response.status})`,
      (data && data.fieldErrors) || null,
    );
  }
  return { data, status: response.status, replayed: response.headers?.get?.('Idempotent-Replayed') === 'true' };
}

async function request(path, options) {
  return (await requestRaw(path, options)).data;
}

/** Human readable text for an error, including per-field messages from the server. */
export function describeError(error) {
  if (!(error instanceof ApiError)) return 'Something went wrong. Please try again.';
  const fields = error.fieldErrors ? Object.entries(error.fieldErrors).map(([k, v]) => `${k}: ${v}`) : [];
  return fields.length ? `${error.message} (${fields.join('; ')})` : error.message;
}

const json = (method, body) => ({ method, body });

export const api = {
  // --- authentication (public) ---
  login: (email, password) => request('/auth/login', { ...json('POST', { email, password }), auth: false }),
  register: (payload) => request('/auth/register', { ...json('POST', payload), auth: false }),
  logout: () => request('/auth/logout', { method: 'POST' }),

  // --- profile ---
  me: () => request('/users/me'),
  updateMe: (payload) => request('/users/me', json('PUT', payload)),
  changePassword: (currentPassword, newPassword) =>
    request('/users/me/password', json('POST', { currentPassword, newPassword })),

  // --- customer: accounts ---
  accounts: () => request('/accounts'),
  createAccount: (currency) => request('/accounts', json('POST', { currency })),
  account: (id) => request(`/accounts/${encodeURIComponent(id)}`),
  ledger: (id, page = 0, size = 10) => request(`/accounts/${encodeURIComponent(id)}/ledger${buildQuery({ page, size })}`),

  // --- customer: money movement (each needs an Idempotency-Key) ---
  deposit: (payload, idempotencyKey) =>
    requestRaw('/transactions/deposit', { ...json('POST', payload), idempotencyKey }),
  withdraw: (payload, idempotencyKey) =>
    requestRaw('/transactions/withdraw', { ...json('POST', payload), idempotencyKey }),
  transfer: (payload, idempotencyKey) =>
    requestRaw('/transactions/transfer', { ...json('POST', payload), idempotencyKey }),
  transactions: ({ accountId, page = 0, size = 10 } = {}) =>
    request(`/transactions${buildQuery({ accountId, page, size })}`),

  // --- admin ---
  adminUsers: (page = 0, size = 20) => request(`/admin/users${buildQuery({ page, size })}`),
  adminCreateUser: (payload) => request('/admin/users', json('POST', payload)),
  adminSetUserStatus: (id, status) => request(`/admin/users/${encodeURIComponent(id)}/status`, json('PATCH', { status })),
  adminSetUserRole: (id, role) => request(`/admin/users/${encodeURIComponent(id)}/role`, json('PATCH', { role })),
  adminCreateAccount: (userId, currency) =>
    request(`/admin/users/${encodeURIComponent(userId)}/accounts`, json('POST', { currency })),
  adminAccounts: (page = 0, size = 20) => request(`/admin/accounts${buildQuery({ page, size })}`),
  adminSetAccountStatus: (id, status) =>
    request(`/admin/accounts/${encodeURIComponent(id)}/status`, json('PATCH', { status })),

  // --- auditor (read only) ---
  auditorAccounts: (page = 0, size = 20) => request(`/auditor/accounts${buildQuery({ page, size })}`),
  auditorTransactions: (page = 0, size = 20) => request(`/auditor/transactions${buildQuery({ page, size })}`),
  auditorTransactionLedger: (id) => request(`/auditor/transactions/${encodeURIComponent(id)}/ledger`),
  auditorIntegrity: () => request('/auditor/ledger-integrity'),

  // --- audit trail (admin + auditor, read only) ---
  auditEvents: ({ action, page = 0, size = 20 } = {}) => request(`/audit-events${buildQuery({ action, page, size })}`),
};
