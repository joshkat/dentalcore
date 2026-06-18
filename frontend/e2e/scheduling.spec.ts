import { expect, test, type Page } from '@playwright/test';

/**
 * Phase E scheduling tools: recurring booking and appointment confirmation.
 * Uses a dedicated provider so seeded hours/time-off can't reject bookings,
 * and far-future weekday slots to dodge collisions in the shared dev DB.
 */
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@dentalcore.local';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'change-me-admin-1';

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(ADMIN_EMAIL);
  await page.getByLabel('Password').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible();
}

function nav(page: Page) {
  return page.getByRole('navigation', { name: 'Main navigation' });
}

test('recurring booking creates a series and confirmation can be sent', async ({ page }) => {
  await login(page);

  // dedicated provider (no hours template = always bookable)
  const stamp = Date.now().toString().slice(-7);
  const providerLast = `Recur${stamp}`;
  await nav(page).getByRole('link', { name: 'Providers' }).click();
  await page.getByRole('button', { name: 'New provider' }).click();
  const pd = page.getByRole('dialog');
  await pd.getByLabel('First name').fill('E2E');
  await pd.getByLabel('Last name').fill(providerLast);
  await pd.getByLabel('NPI').fill(`46${stamp}0`);
  await pd.getByRole('button', { name: 'Create provider' }).click();
  await expect(pd).toBeHidden();

  // dedicated operatory too: unique provider + unique operatory means this
  // series can never collide with appointments left by earlier runs
  const opName = `RecurOp ${stamp}`;
  await nav(page).getByRole('link', { name: 'Schedule' }).click();
  await page.getByRole('button', { name: 'Operatories' }).click();
  const ops = page.getByRole('dialog');
  await ops.getByPlaceholder(/operatory/i).fill(opName);
  await ops.getByRole('button', { name: 'Add', exact: true }).click();
  await expect(ops.getByText(opName)).toBeVisible();
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: 'New appointment' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Patient').fill('Demoson');
  await dialog.getByRole('button', { name: /Demoson, Emma \(/ }).first().click();
  await dialog.getByLabel('Provider').selectOption({ label: `${providerLast}, E2E (DENTIST)` });
  await dialog.getByLabel('Operatory').selectOption({ label: opName });

  // far-future Monday, on a run-specific week so this series is the only
  // thing on that calendar view (avoids clicking another run's Demoson appt)
  const d = new Date();
  d.setDate(d.getDate() + 90 + (Number(stamp.slice(-2)) % 40) * 7);
  while (d.getDay() !== 1) d.setDate(d.getDate() + 1);
  const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
  await dialog.getByLabel('Date').fill(iso);
  await dialog.getByLabel('Start').fill('08:00');

  // make it a 3-week series
  await dialog.getByLabel('Repeat').selectOption('WEEKLY');
  await dialog.getByLabel('Occurrences').fill('3');
  await dialog.getByRole('button', { name: 'Book appointment' }).click();
  await expect(dialog).toBeHidden();

  // open the first occurrence and send a confirmation
  await page.goto(`/schedule?date=${iso}`);
  await page.getByTitle('Demoson, Emma').first().click();
  const detail = page.getByRole('dialog');
  await detail.getByRole('button', { name: /confirmation/i }).click();
  await expect(detail.getByText('Confirmation sent')).toBeVisible();
});
