import test from 'node:test';
import assert from 'node:assert/strict';
import { clearToken, getToken, setToken } from './tokenStore.js';

function fakeSessionStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    dump: () => [...map.entries()],
  };
}

test('token is kept in sessionStorage (never localStorage) and cleared on logout', () => {
  const session = fakeSessionStorage();
  const local = fakeSessionStorage();
  globalThis.sessionStorage = session;
  globalThis.localStorage = local;
  setToken('abc.def.ghi');
  assert.equal(getToken(), 'abc.def.ghi');
  assert.equal(session.dump().length, 1);
  assert.equal(local.dump().length, 0);
  clearToken();
  assert.equal(getToken(), null);
  assert.equal(session.dump().length, 0);
});

test('works in memory when storage throws', () => {
  globalThis.sessionStorage = {
    getItem() { throw new Error('blocked'); },
    setItem() { throw new Error('blocked'); },
    removeItem() { throw new Error('blocked'); },
  };
  setToken('mem-token');
  assert.equal(getToken(), 'mem-token');
  clearToken();
  assert.equal(getToken(), null);
});
