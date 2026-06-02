import { describe, expect, it } from 'vitest';
import { namespaces, resources } from './catalog';

/**
 * GUIDE rule 7: en and es catalogs must stay key-identical — `en` is the
 * fallback, so a missing es key silently renders English.
 */

function keyPaths(catalog: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(catalog).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object') {
      return keyPaths(value as Record<string, unknown>, path);
    }
    return [path];
  });
}

describe('catalog key parity (en ↔ es)', () => {
  it('covers the schedule, checkout, worklists, and recall namespaces', () => {
    for (const namespace of ['schedule', 'checkout', 'worklists', 'recall']) {
      expect(namespaces).toContain(namespace);
    }
  });

  it.each(namespaces)('namespace "%s" has identical keys in en and es', (namespace) => {
    const en = resources.en?.[namespace];
    const es = resources.es?.[namespace];
    expect(en, `missing en/${namespace}.json`).toBeDefined();
    expect(es, `missing es/${namespace}.json`).toBeDefined();
    expect(keyPaths(es!).sort()).toEqual(keyPaths(en!).sort());
  });
});
