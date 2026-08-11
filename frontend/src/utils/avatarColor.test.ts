import { describe, expect, it } from 'vitest';
import { getAvatarGradient } from './avatarColor';

describe('getAvatarGradient', () => {
  it('returns the brand default when no seed is provided', () => {
    expect(getAvatarGradient()).toBe('linear-gradient(135deg, #00A84E 0%, #00C853 100%)');
  });

  it('returns the same gradient for the same user seed', () => {
    expect(getAvatarGradient('nguyen-minh-an')).toBe(getAvatarGradient('nguyen-minh-an'));
  });

  it('distributes different seeds across the configured palette', () => {
    expect(getAvatarGradient('a')).not.toBe(getAvatarGradient('bob'));
  });
});
