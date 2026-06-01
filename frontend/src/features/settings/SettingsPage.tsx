import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Spinner } from '../../components/Spinner';
import { applyLanguage } from '../../i18n';
import type { SupportedLanguage } from '../../types/api';
import { usePreferences, useInstanceConfig, useUpdatePreferences } from './api';

type Card = 'ui' | 'export';

export function SettingsPage() {
  const { t } = useTranslation('settings');
  const config = useInstanceConfig();
  const preferences = usePreferences();
  const update = useUpdatePreferences();

  const [savedCard, setSavedCard] = useState<Card | null>(null);
  const [errorCard, setErrorCard] = useState<Card | null>(null);
  const savedTimer = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => clearTimeout(savedTimer.current), []);

  const flashSaved = (card: Card) => {
    setErrorCard(null);
    setSavedCard(card);
    clearTimeout(savedTimer.current);
    savedTimer.current = setTimeout(() => setSavedCard(null), 2500);
  };

  if (preferences.isLoading) {
    return <Spinner />;
  }

  // Pre-login boot / fetch failure: fall back to "inherit" so the page still
  // renders; a save will simply re-attempt the PUT.
  const prefs = preferences.data ?? {
    uiLanguage: null,
    exportLanguage: null,
  };
  const clinicDefault = config.data?.defaultLanguage ?? null;

  const onUiChange = (choice: SupportedLanguage | null) => {
    // Apply instantly for the UX, then persist.
    applyLanguage(choice ?? clinicDefault ?? 'en');
    update.mutate(
      { uiLanguage: choice, exportLanguage: prefs.exportLanguage },
      {
        onSuccess: (data) => {
          applyLanguage(data.effectiveUiLanguage);
          flashSaved('ui');
        },
        onError: () => setErrorCard('ui'),
      },
    );
  };

  const onExportChange = (choice: SupportedLanguage | null) => {
    update.mutate(
      { uiLanguage: prefs.uiLanguage, exportLanguage: choice },
      {
        onSuccess: () => flashSaved('export'),
        onError: () => setErrorCard('export'),
      },
    );
  };

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900">{t('title')}</h1>
      <div className="mt-6 max-w-xl space-y-6">
        <LanguageChoiceCard
          name="ui-language"
          legend={t('languageCard')}
          hint={t('languageCardHint')}
          value={prefs.uiLanguage}
          clinicDefault={clinicDefault}
          onChange={onUiChange}
          saved={savedCard === 'ui'}
          error={errorCard === 'ui'}
        />
        <LanguageChoiceCard
          name="export-language"
          legend={t('exportCard')}
          hint={t('exportCardHint')}
          value={prefs.exportLanguage}
          clinicDefault={clinicDefault}
          onChange={onExportChange}
          saved={savedCard === 'export'}
          error={errorCard === 'export'}
        />
      </div>
    </div>
  );
}

/** Proper names — identical in both locales on purpose. */
const LANGUAGE_NAMES: Record<SupportedLanguage, string> = {
  en: 'English',
  es: 'Español',
};

interface LanguageChoiceCardProps {
  name: string;
  legend: string;
  hint: string;
  /** null = inherit clinic default */
  value: SupportedLanguage | null;
  clinicDefault: SupportedLanguage | null;
  onChange: (choice: SupportedLanguage | null) => void;
  saved: boolean;
  error: boolean;
}

function LanguageChoiceCard({
  name,
  legend,
  hint,
  value,
  clinicDefault,
  onChange,
  saved,
  error,
}: LanguageChoiceCardProps) {
  const { t } = useTranslation('settings');

  const options: Array<{ choice: SupportedLanguage | null; label: string }> = [
    { choice: 'en', label: t('english') },
    { choice: 'es', label: t('spanish') },
    {
      choice: null,
      label: clinicDefault
        ? t('useClinicDefault', { language: LANGUAGE_NAMES[clinicDefault] })
        : t('useClinicDefaultUnknown'),
    },
  ];

  return (
    <fieldset className="rounded-lg bg-white p-6 shadow">
      <legend className="float-left mb-1 text-base font-semibold text-gray-900">{legend}</legend>
      <span aria-live="polite" className="float-right text-sm font-medium text-green-700">
        {saved && t('saved')}
      </span>
      <p className="clear-both text-sm text-gray-500">{hint}</p>
      <div className="mt-4 space-y-2">
        {options.map((option) => {
          const id = `${name}-${option.choice ?? 'default'}`;
          return (
            <label
              key={id}
              htmlFor={id}
              className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-1.5 text-sm text-gray-900 hover:bg-gray-50"
            >
              <input
                id={id}
                type="radio"
                name={name}
                checked={value === option.choice}
                onChange={() => onChange(option.choice)}
                className="h-4 w-4 border-gray-300 text-brand-600 focus:ring-brand-600"
              />
              {option.label}
            </label>
          );
        })}
      </div>
      {error && (
        <p role="alert" className="mt-3 text-sm text-red-600">
          {t('saveFailed')}
        </p>
      )}
    </fieldset>
  );
}
