import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import type { InstanceConfig, SupportedLanguage } from '../types/api';
import { namespaces, resources } from './catalog';

export const SUPPORTED_LANGUAGES: readonly SupportedLanguage[] = ['en', 'es'];

/**
 * Cache of the *effective* UI language so the first paint after a reload is
 * already in the right language (before config/preferences fetches land).
 * Intentionally kept on logout — a language choice is not sensitive.
 */
export const LANGUAGE_STORAGE_KEY = 'dentalcore.lang';

export function isSupportedLanguage(value: unknown): value is SupportedLanguage {
  return value === 'en' || value === 'es';
}

function readCachedLanguage(): SupportedLanguage | null {
  try {
    const cached = window.localStorage.getItem(LANGUAGE_STORAGE_KEY);
    return isSupportedLanguage(cached) ? cached : null;
  } catch {
    return null;
  }
}

// Synchronous init (catalogs are bundled): components can call useTranslation
// immediately, and the cached language is honored on first paint.
void i18n.use(initReactI18next).init({
  resources,
  ns: namespaces,
  defaultNS: 'common',
  lng: readCachedLanguage() ?? 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false }, // React already escapes
  returnNull: false,
});

/** Switch the active UI language and persist it as the cached effective language. */
export function applyLanguage(language: SupportedLanguage): void {
  try {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  } catch {
    // storage unavailable (private mode etc.) — still switch in memory
  }
  if (i18n.language !== language) void i18n.changeLanguage(language);
}

/**
 * Boot step 2 (called from main.tsx): when the user has no cached language,
 * fall back to the instance default from the public config endpoint.
 * After login, step 3 applies the user's effectiveUiLanguage (see LanguageSync).
 */
export async function bootstrapInstanceLanguage(): Promise<void> {
  if (readCachedLanguage()) return;
  try {
    const response = await fetch('/api/v1/config');
    if (!response.ok) return;
    const config = (await response.json()) as InstanceConfig;
    if (isSupportedLanguage(config.defaultLanguage)) applyLanguage(config.defaultLanguage);
  } catch {
    // backend unreachable — keep 'en' until preferences load
  }
}

export default i18n;
