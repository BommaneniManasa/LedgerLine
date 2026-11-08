import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, setUnauthorizedHandler } from '../lib/api.js';
import { clearToken, getToken, setToken } from '../lib/tokenStore.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  // 'loading' while we check whether a stored session is still valid, then 'ready'
  const [status, setStatus] = useState(getToken() ? 'loading' : 'ready');

  const forgetSession = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  // Any 401 from the API (expired, revoked or tampered token) logs the user out everywhere.
  useEffect(() => {
    setUnauthorizedHandler(forgetSession);
    return () => setUnauthorizedHandler(null);
  }, [forgetSession]);

  // Restore the session after a page refresh. The role always comes from the server, never from storage.
  useEffect(() => {
    if (!getToken()) return undefined;
    let cancelled = false;
    api.me()
      .then((me) => { if (!cancelled) setUser(me); })
      .catch(() => { if (!cancelled) forgetSession(); })
      .finally(() => { if (!cancelled) setStatus('ready'); });
    return () => { cancelled = true; };
  }, [forgetSession]);

  const login = useCallback(async (email, password) => {
    const response = await api.login(email.trim(), password);
    setToken(response.accessToken);
    setUser(response.user);
    return response.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.logout(); // revokes this token on the server
    } catch {
      /* even if the call fails, we still forget the token locally */
    }
    forgetSession();
  }, [forgetSession]);

  const value = useMemo(() => ({ user, status, login, logout, setUser }), [user, status, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
