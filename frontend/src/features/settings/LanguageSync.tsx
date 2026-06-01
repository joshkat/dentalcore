import { useEffect } from 'react';
import { applyLanguage, isSupportedLanguage } from '../../i18n';
import { usePreferences } from './api';

/**
 * Boot step 3: once authenticated (this renders inside Layout), adopt the
 * user's effective UI language from their saved preferences and persist it to
 * the localStorage cache so the next reload paints in the right language.
 * Renders nothing.
 */
export function LanguageSync() {
  const { data } = usePreferences();

  useEffect(() => {
    if (data && isSupportedLanguage(data.effectiveUiLanguage)) {
      applyLanguage(data.effectiveUiLanguage);
    }
  }, [data]);

  return null;
}
