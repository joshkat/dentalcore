import { describe, expect, it } from 'vitest';
import i18n from './index';
import { formatDate, formatDateTime, formatMoney } from './format';

describe('i18n format helpers', () => {
  describe('formatMoney', () => {
    it('formats USD in English', () => {
      expect(formatMoney(1234.5, 'en')).toBe('$1,234.50');
      expect(formatMoney(0, 'en')).toBe('$0.00');
    });

    it('formats USD in Spanish (es-MX keeps US-style grouping)', () => {
      const result = formatMoney(1234.5, 'es');
      expect(result).toContain('1,234.50');
      // explicit USD marker ($, US$ or USD depending on ICU version)
      expect(result).toMatch(/\$|USD/);
    });
  });

  describe('formatDate', () => {
    it('formats a date-only ISO string without timezone day-shift', () => {
      expect(formatDate('2026-06-12', undefined, 'en')).toBe('Jun 12, 2026');
    });

    it('renders Spanish month names', () => {
      const result = formatDate('2026-06-12', undefined, 'es');
      expect(result.toLowerCase()).toContain('jun');
      expect(result).toContain('12');
      expect(result).toContain('2026');
    });

    it('supports option overrides (long weekday)', () => {
      const es = formatDate('2026-06-12', { weekday: 'long', month: 'long' }, 'es');
      expect(es.toLowerCase()).toContain('viernes');
      expect(es.toLowerCase()).toContain('junio');
      const en = formatDate('2026-06-12', { weekday: 'long', month: 'long' }, 'en');
      expect(en).toContain('Friday');
      expect(en).toContain('June');
    });
  });

  describe('formatDateTime', () => {
    it('includes date and time in both locales', () => {
      const date = new Date(2026, 5, 12, 14, 30);
      const en = formatDateTime(date, 'en');
      expect(en).toContain('Jun 12, 2026');
      expect(en).toMatch(/2:30/);
      const es = formatDateTime(date, 'es');
      expect(es.toLowerCase()).toContain('jun');
      expect(es).toMatch(/2:30/);
    });
  });

  describe('active-language default', () => {
    it('follows i18n.language when no explicit language is given', async () => {
      await i18n.changeLanguage('es');
      expect(formatDate('2026-01-05', { month: 'long' }).toLowerCase()).toContain('enero');
      await i18n.changeLanguage('en');
      expect(formatDate('2026-01-05', { month: 'long' })).toContain('January');
    });
  });
});
