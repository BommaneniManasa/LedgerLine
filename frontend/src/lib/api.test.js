import test from 'node:test';
import assert from 'node:assert/strict';
import { api, ApiError, buildQuery, describeError, setUnauthorizedHandler } from './api.js';
import { getToken, setToken } from './tokenStore.js';

function mockFetch(status, body, headers = {}) {
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init });
    return {
      ok: status >= 200 && status < 300,
      status,
      headers: { get: (n) => headers[n] ?? null },
      text: async () => (body === undefined ? '' : JSON.stringify(body)),
    };
  };
  return calls;
}

test.beforeEach(() => {
  globalThis.sessionStorage = undefined;
  setToken(null);
  setUnauthorizedHandler(null);
});

test('sends bearer token, JSON body and omits credentials', async () => {
  setToken('tok123');
  const calls = mockFetch(200, { ok: true });
  await api.updateMe({ fullName: 'A', phone: '' });
  const { url, init } = calls[0];
  assert.equal(url, '/api/users/me');
  assert.equal(init.method, 'PUT');
  assert.equal(init.headers.Authorization, 'Bearer tok123');
  assert.equal(init.headers['Content-Type'], 'application/json');
  assert.equal(init.credentials, 'omit');
  assert.equal(JSON.parse(init.body).fullName, 'A');
});

test('login is sent without Authorization and body never contains a role', async () => {
  setToken('stale');
  const calls = mockFetch(200, { accessToken: 'x' });
  await api.login('a@b.io', 'password123');
  assert.equal(calls[0].init.headers.Authorization, undefined);
  assert.deepEqual(Object.keys(JSON.parse(calls[0].init.body)).sort(), ['email', 'password']);
});

test('money endpoints send the Idempotency-Key header and report replays', async () => {
  const calls = mockFetch(200, { id: '1' }, { 'Idempotent-Replayed': 'true' });
  const res = await api.transfer({ sourceAccountId: 'a' }, 'key-12345678');
  assert.equal(calls[0].init.headers['Idempotency-Key'], 'key-12345678');
  assert.equal(res.replayed, true);
});

test('401 clears the token and calls the unauthorized handler', async () => {
  setToken('tok');
  let called = 0;
  setUnauthorizedHandler(() => { called += 1; });
  mockFetch(401, { code: 'UNAUTHORIZED', message: 'Invalid or expired token' });
  await assert.rejects(api.accounts(), (e) => e instanceof ApiError && e.status === 401);
  assert.equal(getToken(), null);
  assert.equal(called, 1);
});

test('login 401 (bad password) does not trigger global logout', async () => {
  let called = 0;
  setUnauthorizedHandler(() => { called += 1; });
  mockFetch(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' });
  await assert.rejects(api.login('a@b.io', 'x'), (e) => e.code === 'INVALID_CREDENTIALS');
  assert.equal(called, 0);
});

test('error body fields are exposed; network failure becomes NETWORK_ERROR', async () => {
  mockFetch(400, { code: 'VALIDATION_FAILED', message: 'Request validation failed', fieldErrors: { amount: 'bad' } });
  await assert.rejects(api.accounts(), (e) => {
    assert.equal(e.fieldErrors.amount, 'bad');
    assert.match(describeError(e), /amount: bad/);
    return true;
  });
  globalThis.fetch = async () => { throw new TypeError('fetch failed'); };
  await assert.rejects(api.accounts(), (e) => e.code === 'NETWORK_ERROR' && e.status === 0);
});

test('204 responses and query strings', async () => {
  mockFetch(204, undefined);
  assert.equal(await api.logout(), null);
  assert.equal(buildQuery({ a: 1, b: '', c: null, d: 'x y' }), '?a=1&d=x%20y');
  assert.equal(buildQuery({}), '');
});
