import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/**
 * E2E i18n flow. Requires the full stack running (docker compose up) with the
 * bootstrapped admin: ADMIN_EMAIL / ADMIN_PASSWORD from .env
 * (defaults: admin@dentalcore.local / see .env.example).
 *
 * Language is a server-persisted per-user preference shared across the whole
 * suite, so this spec ALWAYS resets it (afterEach, via the API) — otherwise a
 * mid-test failure would leave the shared admin in Spanish and break every
 * later spec that asserts English copy.
 */
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@dentalcore.local';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'change-me-admin-1';
// The request fixture is a Node client (no CORS); hit the published backend
// port directly rather than the Vite dev server on the page baseURL.
const API = process.env.E2E_API_URL ?? 'http://localhost:8080';

async function apiToken(request: APIRequestContext): Promise<string> {
  const res = await request.post(`${API}/api/v1/auth/login`, {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  return (await res.json()).accessToken as string;
}

async function resetLanguage(request: APIRequestContext) {
  const token = await apiToken(request);
  await request.put(`${API}/api/v1/users/me/preferences`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { uiLanguage: null, exportLanguage: null },
  });
}

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel(/Email|Correo electrónico/).fill(ADMIN_EMAIL);
  await page.getByLabel(/^(Password|Contraseña)$/).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /Sign in|Iniciar sesión/ }).click();
  await expect(
    page.getByRole('heading', { name: /welcome back|bienvenido de nuevo/i }),
  ).toBeVisible();
}

test.describe('internationalization', () => {
  // Belt-and-suspenders: reset before (in case an earlier failure leaked) and
  // after every test, independent of any UI assertion outcome.
  test.beforeEach(async ({ request }) => resetLanguage(request));
  test.afterEach(async ({ request }) => resetLanguage(request));

  test('switches to Español, persists across reload, and restores English', async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: /^(Settings|Configuración)$/ }).click();
    await expect(page.getByRole('group', { name: 'Language / Idioma' })).toBeVisible();

    // Switch the UI language to Español — nav flips instantly (optimistic apply).
    await page
      .getByRole('group', { name: 'Language / Idioma' })
      .getByRole('radio', { name: 'Español', exact: true })
      .click();
    await expect(page.getByRole('link', { name: 'Pacientes' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Agenda' })).toBeVisible();

    // Reload proves the preference actually persisted server-side: a fresh load
    // re-fetches prefs, so Spanish surviving the reload means the PUT stuck.
    // (Reload keeps us on /settings, so assert Spanish there, not the dashboard.)
    await page.reload();
    await expect(page.getByRole('link', { name: 'Pacientes' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Configuración' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();

    // Switch back to English and confirm it sticks across a reload.
    await page
      .getByRole('group', { name: 'Language / Idioma' })
      .getByRole('radio', { name: 'English', exact: true })
      .click();
    await expect(page.getByRole('link', { name: 'Patients' })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('link', { name: 'Patients' })).toBeVisible();
  });

  test('export language preference saves independently of the UI language', async ({
    page,
    request,
  }) => {
    await login(page);
    await page.getByRole('link', { name: /^(Settings|Configuración)$/ }).click();

    const exportCard = page.getByRole('group', {
      name: /Document & export language|Idioma de documentos/,
    });
    await exportCard.getByRole('radio', { name: 'Español', exact: true }).click();

    // UI must stay English — changing the export language must not flip the UI.
    await expect(page.getByRole('link', { name: 'Patients' })).toBeVisible();

    // Verify the export preference persisted server-side (the only durable
    // signal, since it has no visible UI effect).
    await expect
      .poll(async () => {
        const token = await apiToken(request);
        const res = await request.get(`${API}/api/v1/users/me/preferences`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        return (await res.json()).exportLanguage;
      })
      .toBe('es');
  });
});
