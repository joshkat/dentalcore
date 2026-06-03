import { describe, expect, it } from 'vitest';
import { namespaces, resources } from './catalog';

/**
 * GUIDE rule 7: en and es catalogs must stay key-identical — `en` is the
 * fallback, so a missing es key silently renders English. This walks every
 * discovered namespace so new catalogs are covered automatically.
 */

function flattenKeys(catalog: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(catalog).flatMap(([key, value]) =>
    value !== null && typeof value === 'object'
      ? flattenKeys(value as Record<string, unknown>, `${prefix}${key}.`)
      : [`${prefix}${key}`],
  );
}

describe('locale catalogs', () => {
  it('discovers the patients, chart, and notes namespaces', () => {
    expect(namespaces).toEqual(expect.arrayContaining(['patients', 'chart', 'notes']));
  });

  describe.each(namespaces)('namespace %s', (namespace) => {
    it('has identical keys in en and es', () => {
      const en = resources.en?.[namespace];
      const es = resources.es?.[namespace];
      expect(en, `missing en/${namespace}.json`).toBeDefined();
      expect(es, `missing es/${namespace}.json`).toBeDefined();
      expect(flattenKeys(es!).sort()).toEqual(flattenKeys(en!).sort());
    });

    it('has no empty values', () => {
      for (const language of ['en', 'es'] as const) {
        const catalog = resources[language]?.[namespace] ?? {};
        const flat = flattenKeys(catalog);
        for (const key of flat) {
          const value = key
            .split('.')
            .reduce<unknown>((acc, part) => (acc as Record<string, unknown>)[part], catalog);
          expect(value, `${language}/${namespace}:${key}`).toBeTruthy();
        }
      }
    });
  });
});
