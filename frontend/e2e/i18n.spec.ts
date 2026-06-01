import { expect, test } from '@playwright/test';

/**
 * E2E i18n flow. Requires the full stack running (docker compose up) with the
 * bootstrapped admin: ADMIN_EMAIL / ADMIN_PASSWORD from .env
 * (defaults: admin@dentalcore.local / see .env.example).
 *
 * READ_ONLY-safe: the flow only reads pages and updates the signed-in user's
 * own language preferences (PUT /api/v1/users/me/preferences), restoring
 * English at the end so later specs see the default UI.
 */
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@dentalcore.local';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'change-me-admin-1';

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByLabel(/Email|Correo electrónico/).fill(ADMIN_EMAIL);
  await page.getByLabel(/^(Password|Contraseña)$/).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /Sign in|Iniciar sesión/ }).click();
  await expect(
    page.getByRole('heading', { name: /welcome back|bienvenido de nuevo/i }),
  ).toBeVisible();
}

test.describe('internationalization', () => {
  test('switches to Español, persists across reload, and restores English', async ({ page }) => {
    await login(page);

    // Open Settings from the sidebar (label may be in either language if a
    // previous run left a preference behind).
    await page.getByRole('link', { name: /^(Settings|Configuración)$/ }).click();
    await expect(page.getByRole('group', { name: 'Language / Idioma' })).toBeVisible();

    // Switch the UI language to Español (instant apply + saved preference).
    await page
      .getByRole('group', { name: 'Language / Idioma' })
      .getByRole('radio', { name: 'Español' })
      .check();
    await expect(page.getByText('Guardado')).toBeVisible();

    // Nav flips to Spanish without a reload.
    await expect(page.getByRole('link', { name: 'Pacientes' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Agenda' })).toBeVisible();

    // Reload: language persists via the saved preference (and the
    // localStorage cache makes the first paint Spanish too).
    await page.reload();
    await expect(page.getByRole('link', { name: 'Pacientes' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Agenda' })).toBeVisible();
    await expect(page.getByRole('heading', { name: /bienvenido de nuevo/i })).toBeVisible();

    // Spanish page title on the settings page and translated sign-out control.
    await page.getByRole('link', { name: 'Configuración' }).click();
    await expect(page.getByRole('heading', { name: 'Configuración' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();

    // Switch back to English ("English" is a proper noun in both locales).
    await page
      .getByRole('group', { name: 'Language / Idioma' })
      .getByRole('radio', { name: 'English' })
      .check();
    await expect(page.getByText('Saved')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Patients' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Schedule' })).toBeVisible();

    // And it sticks after a reload.
    await page.reload();
    await expect(page.getByRole('link', { name: 'Patients' })).toBeVisible();
  });

  test('export language preference saves independently of the UI language', async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: /^(Settings|Configuración)$/ }).click();

    const exportCard = page.getByRole('group', {
      name: /Document & export language|Idioma de documentos/,
    });
    await exportCard.getByRole('radio', { name: 'Español' }).check();
    await expect(page.getByText(/^(Saved|Guardado)$/)).toBeVisible();

    // UI stays English (export language must not flip the UI).
    await expect(page.getByRole('link', { name: 'Patients' })).toBeVisible();

    // Restore inherit-default for later runs.
    await exportCard.getByRole('radio', { name: /Use clinic default|predeterminado/ }).check();
    await expect(page.getByText(/^(Saved|Guardado)$/)).toBeVisible();
  });
});
