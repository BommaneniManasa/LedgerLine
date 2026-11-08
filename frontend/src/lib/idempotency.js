// Idempotency keys make retries safe: the server remembers a key and returns the ORIGINAL result
// instead of moving money twice. Backend format: 8-100 characters, letters, digits, '-' or '_'.
export function newIdempotencyKey() {
  const c = globalThis.crypto;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  const bytes = new Uint8Array(16);
  c.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Holds ONE key per submission attempt.
 *  - Network error / 5xx / timeout  -> keep the key, so the user's retry is the SAME request (no double payment).
 *  - Success or a definite rejection (4xx) -> renew, so the next submit is a NEW request.
 *  - 409 "still processing" -> keep the key: the first request may still finish.
 */
export function createKeyHolder() {
  let key = null;
  return {
    current() {
      if (!key) key = newIdempotencyKey();
      return key;
    },
    reset() {
      key = null;
    },
  };
}

export function shouldRenewKey(error) {
  if (!error || typeof error.status !== 'number') return false;
  if (error.status === 409 && error.code === 'IDEMPOTENCY_IN_PROGRESS') return false;
  if (error.status === 408 || error.status === 429) return false;
  return error.status >= 400 && error.status < 500;
}
