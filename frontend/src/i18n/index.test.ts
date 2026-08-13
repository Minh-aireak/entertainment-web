/// <reference types="node" />

import { readdirSync, readFileSync } from 'node:fs';
import { extname, join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';
import i18n from './index';

const getLeafKeys = (value: Record<string, unknown>, prefix = ''): string[] =>
  Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return child && typeof child === 'object' && !Array.isArray(child)
      ? getLeafKeys(child as Record<string, unknown>, path)
      : [path];
  }).sort();

const collectSourceFiles = (directory: string): string[] =>
  readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return collectSourceFiles(path);
    if (!['.ts', '.tsx'].includes(extname(entry.name)) || entry.name.includes('.test.')) return [];
    return [path];
  });

describe('i18n catalog', () => {
  it('keeps the English and Vietnamese translation keys in sync', () => {
    const english = i18n.getResourceBundle('en', 'translation') as Record<string, unknown>;
    const vietnamese = i18n.getResourceBundle('vi', 'translation') as Record<string, unknown>;

    expect(getLeafKeys(english)).toEqual(getLeafKeys(vietnamese));
  });

  it('defines every statically referenced translation key', () => {
    const sourceRoot = join(process.cwd(), 'src');
    const missingKeys = collectSourceFiles(sourceRoot).flatMap((sourceFile) => {
      const content = readFileSync(sourceFile, 'utf8');
      const keys = [...content.matchAll(/\bt\(\s*['"]([^'"]+)['"]/g)].map((match) => match[1]);

      return keys
        .filter((key) => !i18n.exists(key, { lng: 'en' }))
        .map((key) => `${relative(sourceRoot, sourceFile)}: ${key}`);
    });

    expect([...new Set(missingKeys)]).toEqual([]);
  });
});
