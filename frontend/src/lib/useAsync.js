import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Runs an async loader when `deps` change and tracks loading / error / data.
 * Ignores results from outdated calls (e.g. the user changed the page quickly).
 */
export function useAsync(loader, deps) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const counter = useRef(0);

  const run = useCallback(() => {
    const id = ++counter.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    loader()
      .then((data) => { if (id === counter.current) setState({ data, error: null, loading: false }); })
      .catch((error) => { if (id === counter.current) setState({ data: null, error, loading: false }); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    run();
    return () => { counter.current += 1; };
  }, [run]);

  return { ...state, reload: run };
}
