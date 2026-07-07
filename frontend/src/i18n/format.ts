import type { SupportedLanguage } from '../types/api';
import i18n, { isSupportedLanguage } from './index';

/**
 * Locale mapping for Intl formatting. The product is deployed in the
 * Dominican Republic: Spanish uses es-DO, which renders Dominican pesos as
 * RD$1,234.50 (the DR groups with commas and a period decimal, like the US).
 */
const LOCALES: Record<SupportedLanguage, string> = {
  en: 'en-US',
  es: 'es-DO',
};

function activeLocale(language?: SupportedLanguage): string {
  if (language) return LOCALES[language];
  return isSupportedLanguage(i18n.language) ? LOCALES[i18n.language] : LOCALES.en;
}

function toDate(value: string | number | Date): Date {
  // Date-only ISO strings (YYYY-MM-DD) are parsed as UTC midnight by Date;
  // anchor them to noon so they render as the same calendar day in any
  // plausible clinic zone (the DR runs on UTC-4 year-round).
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return new Date(`${value}T12:00:00`);
  }
  return value instanceof Date ? value : new Date(value);
}

/** e.g. en: "Jun 12, 2026" — es: "12 jun 2026". Pass options to override the style. */
export function formatDate(
  value: string | number | Date,
  options?: Intl.DateTimeFormatOptions,
  language?: SupportedLanguage,
): string {
  return new Intl.DateTimeFormat(
    activeLocale(language),
    options ?? { dateStyle: 'medium' },
  ).format(toDate(value));
}

/** e.g. en: "Jun 12, 2026, 2:30 PM" — es: "12 jun 2026, 2:30 p.m." */
export function formatDateTime(
  value: string | number | Date,
  language?: SupportedLanguage,
): string {
  return new Intl.DateTimeFormat(activeLocale(language), {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(toDate(value));
}

/** Dominican pesos in the active locale, e.g. es: "RD$1,234.50" — en: "DOP 1,234.50". */
export function formatMoney(amount: number, language?: SupportedLanguage): string {
  return new Intl.NumberFormat(activeLocale(language), {
    style: 'currency',
    currency: 'DOP',
  }).format(amount);
}
