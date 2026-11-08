import test from 'node:test';
import assert from 'node:assert/strict';
import { createKeyHolder, newIdempotencyKey, shouldRenewKey } from './idempotency.js';
import { ApiError } from './api.js';

test('keys match the backend format', () => {
  assert.match(newIdempotencyKey(), /^[A-Za-z0-9_-]{8,100}$/);
  assert.notEqual(newIdempotencyKey(), newIdempotencyKey());
});

test('holder returns the same key until reset', () => {
  const h = createKeyHolder();
  const first = h.current();
  assert.equal(h.current(), first);
  h.reset();
  assert.notEqual(h.current(), first);
});

test('key is kept after network/5xx errors and renewed after definite rejections', () => {
  assert.equal(shouldRenewKey(new ApiError(0, 'NETWORK_ERROR', 'x')), false);
  assert.equal(shouldRenewKey(new ApiError(500, 'INTERNAL_ERROR', 'x')), false);
  assert.equal(shouldRenewKey(new ApiError(409, 'IDEMPOTENCY_IN_PROGRESS', 'x')), false);
  assert.equal(shouldRenewKey(new ApiError(422, 'INSUFFICIENT_FUNDS', 'x')), true);
  assert.equal(shouldRenewKey(new ApiError(400, 'VALIDATION_FAILED', 'x')), true);
  assert.equal(shouldRenewKey(null), false);
});
