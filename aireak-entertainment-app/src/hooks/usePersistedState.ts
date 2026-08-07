import { useCallback, useState } from 'react';

/**
 * In-memory, module-level store. Survives client-side route navigation (so data
 * fetched once isn't re-fetched every time a user switches tabs) but resets on a
 * real browser reload, since the module is re-evaluated from scratch then.
 */
const persistedStore = new Map<string, unknown>();

export function hasPersistedState(key: string): boolean {
  return persistedStore.has(key);
}

export function clearPersistedState(key: string): void {
  persistedStore.delete(key);
}

export function usePersistedState<T>(key: string, initialValue: T) {
  const [state, setState] = useState<T>(() =>
    persistedStore.has(key) ? (persistedStore.get(key) as T) : initialValue,
  );

  const setPersistedState = useCallback(
    (value: T | ((prev: T) => T)) => {
      setState((prev) => {
        const next = typeof value === 'function' ? (value as (prev: T) => T)(prev) : value;
        persistedStore.set(key, next);
        return next;
      });
    },
    [key],
  );

  return [state, setPersistedState] as const;
}
