import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Node >= 24 ships an experimental global `localStorage` that is undefined
// unless --localstorage-file is passed; its presence makes Vitest skip copying
// jsdom's real implementation onto the global. Provide an in-memory stand-in.
if (typeof window !== 'undefined' && !window.localStorage) {
  const store = new Map<string, string>();
  const localStorageStub: Storage = {
    get length() {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: (key) => store.get(key) ?? null,
    key: (index) => [...store.keys()][index] ?? null,
    removeItem: (key) => void store.delete(key),
    setItem: (key, value) => void store.set(key, String(value)),
  };
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: localStorageStub,
  });
}

// Initialize the real i18n instance (English catalogs) for all component
// tests. Dynamic import so it runs *after* the localStorage stub above.
const { default: i18n } = await import('../i18n');

afterEach(async () => {
  cleanup();
  // Tests that switch language must not leak it into the next test.
  if (i18n.language !== 'en') await i18n.changeLanguage('en');
});
