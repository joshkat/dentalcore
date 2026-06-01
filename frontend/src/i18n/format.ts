import type { SupportedLanguage } from '../types/api';
import i18n, { isSupportedLanguage } from './index';

/**
 * Locale mapping for Intl formatting. Spanish uses es-MX: the dominant
 * Spanish-speaking patient population for US dental practices is Mexican
 * Spanish, and es-MX keeps US-style currency/number grouping ($1,234.50).
 */
const LOCALES: Record<SupportedLanguage, string> = {
  en: 'en-US',
  es: 'es-MX',
};

function activeLocale(language?: SupportedLanguage): string {
  if (language) return LOCALES[language];
  return isSupportedLanguage(i18n.language) ? LOCALES[i18n.language] : LOCALES.en;
}

function toDate(value: string | number | Date): Date {
  // Date-only ISO strings (YYYY-MM-DD) are parsed as UTC midnight by Date;
  // anchor them to noon so they render as the same calendar day in all US zones.
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

/** USD currency in the active locale, e.g. en: "$1,234.50". */
export function formatMoney(amount: number, language?: SupportedLanguage): string {
  return new Intl.NumberFormat(activeLocale(language), {
    style: 'currency',
    currency: 'USD',
  }).format(amount);
}
