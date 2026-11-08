import { describeError } from '../lib/api.js';

/** Success / error message. Errors use role="alert" so screen readers announce them immediately. */
export function Banner({ kind = 'info', children }) {
  if (!children) return null;
  return (
    <div className={`banner banner-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function ErrorBanner({ error }) {
  if (!error) return null;
  return <Banner kind="error">{describeError(error)}</Banner>;
}

export function Spinner({ label = 'Loading' }) {
  return (
    <div className="loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" /> {label}...
    </div>
  );
}

/** Shows spinner / error / children for a useAsync() result. */
export function AsyncView({ state, children }) {
  if (state.loading && !state.data) return <Spinner />;
  if (state.error) return <ErrorBanner error={state.error} />;
  if (!state.data) return null;
  return children(state.data);
}
