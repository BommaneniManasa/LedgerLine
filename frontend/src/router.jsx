// A tiny router built on the browser History API (no react-router dependency).
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useAuth } from './context/AuthContext.jsx';

const RouterContext = createContext(null);

export function Router({ children }) {
  const [path, setPath] = useState(window.location.pathname);

  useEffect(() => {
    const onPop = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  const navigate = useCallback((to, { replace = false } = {}) => {
    if (to === window.location.pathname + window.location.search) return;
    if (replace) window.history.replaceState({}, '', to);
    else window.history.pushState({}, '', to);
    setPath(window.location.pathname);
    window.scrollTo(0, 0);
  }, []);

  const value = useMemo(() => ({ path, navigate }), [path, navigate]);
  return <RouterContext.Provider value={value}>{children}</RouterContext.Provider>;
}

export function useLocation() {
  return useContext(RouterContext).path;
}

export function useNavigate() {
  return useContext(RouterContext).navigate;
}

/** Route params for the matched route, e.g. /accounts/:id -> { id } */
const ParamsContext = createContext({});
export function useParams() {
  return useContext(ParamsContext);
}

export function Link({ to, children, onClick, ...rest }) {
  const navigate = useNavigate();
  const path = useLocation();
  const handle = (e) => {
    if (onClick) onClick(e);
    // let the browser handle ctrl/cmd-click, new tabs etc.
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    e.preventDefault();
    navigate(to);
  };
  const active = path === to || (to !== '/' && path.startsWith(`${to}/`));
  return (
    <a href={to} onClick={handle} aria-current={active ? 'page' : undefined} {...rest}>
      {children}
    </a>
  );
}

export function Navigate({ to, replace = true }) {
  const navigate = useNavigate();
  useEffect(() => {
    navigate(to, { replace });
  }, [to, replace, navigate]);
  return null;
}

function matchPath(pattern, path) {
  if (pattern === '*') return {};
  const p = pattern.split('/').filter(Boolean);
  const a = path.split('/').filter(Boolean);
  if (p.length !== a.length) return null;
  const params = {};
  for (let i = 0; i < p.length; i += 1) {
    if (p[i].startsWith(':')) params[p[i].slice(1)] = decodeURIComponent(a[i]);
    else if (p[i] !== a[i]) return null;
  }
  return params;
}

/** routes: [{ path, element }] - first match wins, '*' is the fallback. */
export function Routes({ routes }) {
  const path = useLocation();
  for (const route of routes) {
    const params = matchPath(route.path, path);
    if (params) return <ParamsContext.Provider value={params}>{route.element}</ParamsContext.Provider>;
  }
  return null;
}

/**
 * Hides a page from people who may not use it.
 * IMPORTANT: this is only a convenience for the UI. The real access control is on the backend, which
 * rejects the API calls anyway (403) no matter what the browser shows.
 */
export function ProtectedRoute({ roles, children }) {
  const { user, status } = useAuth();
  if (status === 'loading') return <div className="center-screen"><div className="spinner" aria-label="Loading" /></div>;
  if (!user) return <Navigate to="/login" />;
  if (roles && !roles.includes(user.role)) return <Navigate to="/" />;
  return children;
}

export function homeFor(role) {
  if (role === 'ADMIN') return '/admin';
  if (role === 'AUDITOR') return '/auditor';
  return '/dashboard';
}
