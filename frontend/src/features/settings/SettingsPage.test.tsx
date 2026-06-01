import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import i18n, { LANGUAGE_STORAGE_KEY } from '../../i18n';
import type { UserPreferences } from '../../types/api';
import { SettingsPage } from './SettingsPage';

const apiMock = vi.fn();

vi.mock('../../lib/api', () => ({
  api: (...args: unknown[]) => apiMock(...args),
}));

function mockBackend(initial: UserPreferences) {
  let prefs = { ...initial };
  apiMock.mockImplementation((path: string, options?: { method?: string; body?: unknown }) => {
    if (path === '/api/v1/config') {
      return Promise.resolve({ defaultLanguage: 'en', supportedLanguages: ['en', 'es'] });
    }
    if (path === '/api/v1/users/me/preferences') {
      if (options?.method === 'PUT') {
        const body = options.body as Pick<UserPreferences, 'uiLanguage' | 'exportLanguage'>;
        prefs = {
          ...body,
          effectiveUiLanguage: body.uiLanguage ?? 'en',
          effectiveExportLanguage: body.exportLanguage ?? 'en',
        };
      }
      return Promise.resolve(prefs);
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <SettingsPage />
    </QueryClientProvider>,
  );
}

describe('SettingsPage', () => {
  beforeEach(() => {
    apiMock.mockReset();
    window.localStorage.removeItem(LANGUAGE_STORAGE_KEY);
  });

  it('shows both language cards with the clinic default spelled out', async () => {
    mockBackend({
      uiLanguage: null,
      exportLanguage: null,
      effectiveUiLanguage: 'en',
      effectiveExportLanguage: 'en',
    });
    renderPage();

    const uiCard = await screen.findByRole('group', { name: 'Language / Idioma' });
    expect(within(uiCard).getByRole('radio', { name: 'English' })).toBeInTheDocument();
    expect(within(uiCard).getByRole('radio', { name: 'Español' })).toBeInTheDocument();
    await waitFor(() =>
      expect(
        within(uiCard).getByRole('radio', { name: 'Use clinic default (English)' }),
      ).toBeChecked(),
    );

    expect(screen.getByRole('group', { name: 'Document & export language' })).toBeInTheDocument();
  });

  it('selecting Español switches the UI language instantly and PUTs the preference', async () => {
    mockBackend({
      uiLanguage: null,
      exportLanguage: null,
      effectiveUiLanguage: 'en',
      effectiveExportLanguage: 'en',
    });
    renderPage();

    const uiCard = await screen.findByRole('group', { name: 'Language / Idioma' });
    await userEvent.click(within(uiCard).getByRole('radio', { name: 'Español' }));

    await waitFor(() => expect(i18n.language).toBe('es'));
    expect(apiMock).toHaveBeenCalledWith('/api/v1/users/me/preferences', {
      method: 'PUT',
      body: { uiLanguage: 'es', exportLanguage: null },
    });
    // effective language cached for instant first paint on next reload
    expect(window.localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('es');
    // saved confirmation, now rendered in Spanish
    expect(await screen.findByText('Guardado')).toBeInTheDocument();
  });

  it('changing the export language PUTs without touching the UI language', async () => {
    mockBackend({
      uiLanguage: null,
      exportLanguage: null,
      effectiveUiLanguage: 'en',
      effectiveExportLanguage: 'en',
    });
    renderPage();

    const exportCard = await screen.findByRole('group', { name: 'Document & export language' });
    await userEvent.click(within(exportCard).getByRole('radio', { name: 'Español' }));

    await waitFor(() =>
      expect(apiMock).toHaveBeenCalledWith('/api/v1/users/me/preferences', {
        method: 'PUT',
        body: { uiLanguage: null, exportLanguage: 'es' },
      }),
    );
    expect(i18n.language).toBe('en');
    expect(await screen.findByText('Saved')).toBeInTheDocument();
  });

  it('falling back to clinic default applies the instance language', async () => {
    mockBackend({
      uiLanguage: 'es',
      exportLanguage: null,
      effectiveUiLanguage: 'es',
      effectiveExportLanguage: 'en',
    });
    renderPage();

    const uiCard = await screen.findByRole('group', { name: 'Language / Idioma' });
    await waitFor(() =>
      expect(within(uiCard).getByRole('radio', { name: 'Español' })).toBeChecked(),
    );
    await userEvent.click(
      within(uiCard).getByRole('radio', { name: /Use clinic default|predeterminado/ }),
    );

    await waitFor(() => expect(i18n.language).toBe('en'));
    expect(apiMock).toHaveBeenCalledWith('/api/v1/users/me/preferences', {
      method: 'PUT',
      body: { uiLanguage: null, exportLanguage: null },
    });
  });
});
