import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import i18n from '../../i18n';
import LanguageSwitcher from './LanguageSwitcher';

describe('LanguageSwitcher', () => {
  beforeEach(async () => {
    localStorage.removeItem('aireak-language');
    await i18n.changeLanguage('vi');
  });

  afterEach(async () => {
    await i18n.changeLanguage('vi');
    localStorage.removeItem('aireak-language');
  });

  it('switches to English and persists the selected language', async () => {
    render(<LanguageSwitcher />);

    fireEvent.click(screen.getByRole('button', { name: 'Chuyển sang Tiếng Anh' }));

    await waitFor(() => {
      expect(i18n.resolvedLanguage).toBe('en');
    });

    expect(screen.getByRole('button', { name: 'Switch to Vietnamese' })).toHaveTextContent('en');
    expect(localStorage.getItem('aireak-language')).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });
});
