import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { formatRelativeTime } from './time';

describe('formatRelativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-11T12:00:00.000Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('uses the Vietnamese just-now label for differences below one minute', () => {
    expect(formatRelativeTime('2026-08-11T11:59:31.000Z')).toBe('Vừa xong');
  });

  it('uses the English just-now label when requested', () => {
    expect(formatRelativeTime('2026-08-11T12:00:30.000Z', 'en')).toBe('Just now');
  });

  it('formats past timestamps using the largest matching unit', () => {
    expect(formatRelativeTime('2026-08-11T10:00:00.000Z', 'en')).toBe('2 hours ago');
    expect(formatRelativeTime('2026-08-04T12:00:00.000Z', 'en')).toBe('last week');
  });

  it('formats future timestamps', () => {
    expect(formatRelativeTime('2026-08-12T12:00:00.000Z', 'en')).toBe('tomorrow');
  });

  it('accepts Date instances', () => {
    expect(formatRelativeTime(new Date('2026-08-11T11:55:00.000Z'), 'en')).toBe('5 minutes ago');
  });
});
