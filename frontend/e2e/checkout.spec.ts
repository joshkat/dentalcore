import { expect, test, type Page } from '@playwright/test';

/**
 * Core front-desk workflow against the full running stack (docker compose up):
 * book today → check in → guided checkout (complete work, take payment,
 * complete appointment) → verify the day sheet and worklists.
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

/** Sidebar links — dashboard tiles are links too, so scope to the nav. */
function nav(page: Page, name: string) {
  return page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('link', { name, exact: true });
}

function localIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}

const parseMoney = (text: string) => Number(text.replace(/[^0-9.]/g, ''));

test.describe('checkout flow', () => {
  test('check out a visit, then see it on the day sheet and worklists', async ({ page }) => {
    await login(page);

    // ---- dedicated provider: dev-DB providers accumulate human-entered
    // time-off and hours templates that can reject a booking for today ----
    const stamp = Date.now().toString().slice(-7);
    const providerLast = `Checkout${stamp}`;
    await nav(page, 'Providers').click();
    await page.getByRole('button', { name: 'New provider' }).click();
    const providerDialog = page.getByRole('dialog');
    await providerDialog.getByLabel('First name').fill('E2E');
    await providerDialog.getByLabel('Last name').fill(providerLast);
    await providerDialog.getByLabel('NPI').fill(`31${stamp}0`);
    await providerDialog.getByRole('button', { name: 'Create provider' }).click();
    await expect(providerDialog).toBeHidden();

    // ---- book an appointment for today with an attached procedure ----
    await nav(page, 'Schedule').click();
    await page.getByRole('button', { name: 'New appointment' }).click();

    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Patient').fill('Demoson');
    await dialog
      .getByRole('button', { name: /Demoson, Emma \(/ })
      .first()
      .click();
    await dialog.getByLabel('Provider').selectOption({ label: `${providerLast}, E2E (DENTIST)` });

    const iso = localIso(new Date());
    await dialog.getByLabel('Date').fill(iso);

    // attach a procedure so the checkout panel has a row to complete
    await dialog.getByLabel('Search procedures').fill('D1110');
    await dialog
      .getByRole('button', { name: /D1110/ })
      .first()
      .click();

    // today at a random short slot; previous runs of this suite leave their own
    // today-appointments behind in the shared dev DB, so on an operatory
    // conflict (dialog stays open with a 409 alert) retry a different slot
    // rotate operatories too: earlier runs may have filled one room's day
    const operatoryCount =
      (await dialog.getByLabel('Operatory').locator('option').count()) - 1;
    let appointmentId = '';
    for (let attempt = 0; attempt < 8 && !appointmentId; attempt++) {
      await dialog
        .getByLabel('Operatory')
        .selectOption({ index: 1 + (attempt % Math.max(operatoryCount, 1)) });
      const hour = 9 + Math.floor(Math.random() * 8);
      const minute = [0, 15, 30, 45][Math.floor(Math.random() * 4)];
      const start = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
      await dialog.getByLabel('Start').fill(start);
      const createdResponse = page.waitForResponse(
        (r) => r.url().includes('/api/v1/appointments') && r.request().method() === 'POST',
      );
      await dialog.getByRole('button', { name: 'Book appointment' }).click();
      const response = await createdResponse;
      if (response.status() === 201) {
        appointmentId = ((await response.json()) as { id: string }).id;
      }
    }
    expect(appointmentId, 'no free slot found after 8 attempts').not.toBe('');
    await expect(dialog).toBeHidden();

    // ---- check in ----
    await page.goto(`/schedule?date=${iso}&appointment=${appointmentId}`);
    await expect(page.getByText('Scheduled', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Checked in' }).click();
    await expect(page.getByText('Checked in', { exact: true }).first()).toBeVisible();

    // ---- guided checkout ----
    await page.getByRole('button', { name: 'Check out' }).click();
    const checkout = page.getByRole('dialog', { name: 'Check out' });
    await expect(checkout).toBeVisible();

    // complete the first procedure row → it flips green with an Undo link
    await checkout.getByRole('button', { name: 'Complete', exact: true }).first().click();
    await expect(checkout.getByRole('button', { name: 'Undo' })).toBeVisible();

    // take a $1 cash payment
    await checkout.getByLabel('Amount ($)').fill('1');
    await checkout.getByLabel('Method').selectOption('CASH');
    await checkout.getByRole('button', { name: 'Take payment' }).click();
    await expect(checkout.getByText(/Payment of \$1\.00 recorded/)).toBeVisible();

    // complete the appointment
    await checkout.getByRole('button', { name: 'Complete appointment' }).click();
    await expect(checkout.getByText('Appointment completed.')).toBeVisible();
    await checkout.getByRole('button', { name: 'Close' }).click();

    // status shows Completed on the appointment detail
    await page.goto(`/schedule?date=${iso}&appointment=${appointmentId}`);
    await expect(page.getByText('Completed', { exact: true }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Close' }).click();

    // ---- day sheet shows today's production and collections ----
    await nav(page, 'Reports').click();
    await page.getByLabel('Report', { exact: true }).selectOption('Day sheet');
    const production = page.getByTestId('day-sheet-production');
    await expect(production).toBeVisible();
    await expect
      .poll(async () => parseMoney(await production.innerText()))
      .toBeGreaterThan(0);
    await expect
      .poll(async () => parseMoney(await page.getByTestId('day-sheet-collections').innerText()))
      .toBeGreaterThan(0);

    // ---- worklists: both tabs render ----
    await nav(page, 'Worklists').click();
    await expect(page.getByRole('heading', { name: 'Worklists' })).toBeVisible();
    await expect(
      page.getByRole('table').or(page.getByText('No unscheduled treatment.')).first(),
    ).toBeVisible();
    await page.getByRole('button', { name: 'ASAP list' }).click();
    await expect(
      page.getByRole('table').or(page.getByText('No ASAP requests.')).first(),
    ).toBeVisible();
  });
});
