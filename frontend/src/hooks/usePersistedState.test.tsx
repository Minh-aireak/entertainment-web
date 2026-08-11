import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import {
  clearPersistedState,
  hasPersistedState,
  usePersistedState,
} from './usePersistedState';

const TEST_KEYS = ['persisted-state-value', 'persisted-state-updater', 'persisted-state-clear'];

describe('usePersistedState', () => {
  afterEach(() => {
    TEST_KEYS.forEach(clearPersistedState);
  });

  it('keeps a value when the hook is unmounted and mounted again', () => {
    const first = renderHook(() => usePersistedState('persisted-state-value', 'initial'));

    act(() => first.result.current[1]('saved'));
    first.unmount();

    const second = renderHook(() => usePersistedState('persisted-state-value', 'fallback'));
    expect(second.result.current[0]).toBe('saved');
    expect(hasPersistedState('persisted-state-value')).toBe(true);
  });

  it('supports functional state updates', () => {
    const { result } = renderHook(() => usePersistedState('persisted-state-updater', 2));

    act(() => result.current[1]((previous) => previous * 3));

    expect(result.current[0]).toBe(6);
  });

  it('uses the supplied initial value after a key is cleared', () => {
    const first = renderHook(() => usePersistedState('persisted-state-clear', 'old'));
    act(() => first.result.current[1]('stored'));
    first.unmount();

    clearPersistedState('persisted-state-clear');

    const second = renderHook(() => usePersistedState('persisted-state-clear', 'new'));
    expect(second.result.current[0]).toBe('new');
    expect(hasPersistedState('persisted-state-clear')).toBe(false);
  });
});
