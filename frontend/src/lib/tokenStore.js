// Where the JWT lives while the user is logged in.
// SECURITY: memory + sessionStorage only. Never localStorage (it outlives the tab), never a URL.
// sessionStorage is cleared when the tab closes. The in-memory copy is the fallback if storage is blocked.
const KEY = 'll.session';
let memoryToken = null;

function storage() {
  try {
    return globalThis.sessionStorage || null;
  } catch {
    return null;
  }
}

export function getToken() {
  if (memoryToken) return memoryToken;
  try {
    const stored = storage()?.getItem(KEY);
    if (stored) memoryToken = stored;
  } catch {
    /* storage unavailable: stay in memory only */
  }
  return memoryToken;
}

export function setToken(token) {
  memoryToken = token || null;
  try {
    if (token) storage()?.setItem(KEY, token);
    else storage()?.removeItem(KEY);
  } catch {
    /* ignore */
  }
}

export function clearToken() {
  setToken(null);
}
