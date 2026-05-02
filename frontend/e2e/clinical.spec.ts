import { expect, test, type Page } from '@playwright/test';

/**
 * Clinical golden path against the full running stack (docker compose up).
 * Creates its own patient, charts a tooth, books and works an appointment.
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

test.describe('clinical flows', () => {
  test('register patient, chart a condition, see it on the odontogram', async ({ page }) => {
    await login(page);

    // register
    const last = `E2EChart${Date.now()}`;
    await nav(page, 'Patients').click();
    await page.getByRole('button', { name: 'New patient' }).click();
    await page.getByLabel('First name').fill('Tess');
    await page.getByLabel('Last name').fill(last);
    await page.getByLabel('Date of birth').fill('1990-05-05');
    await page.getByLabel('Number').fill('555-321-9876');
    await page.getByRole('button', { name: 'Register patient' }).click();
    await expect(page.getByRole('heading', { name: new RegExp(last) })).toBeVisible();

    // chart caries on tooth 14
    await page.getByRole('button', { name: 'Chart' }).click();
    await page.getByRole('button', { name: 'Tooth 14' }).click();
    await page.getByLabel('Add condition').selectOption('CARIES');
    await page.getByRole('button', { name: 'M', exact: true }).click();
    await page.getByRole('button', { name: 'O', exact: true }).click();
    await page.getByRole('button', { name: 'Chart condition' }).click();

    await expect(page.getByText('Caries (MO)')).toBeVisible();
  });

  test('book an appointment from the calendar and check the patient in', async ({ page }) => {
    await login(page);
    await nav(page, 'Schedule').click();
    await page.getByRole('button', { name: 'New appointment' }).click();

    // pick an existing patient via the typeahead, scoped to the dialog
    // (calendar blocks behind the modal share the patient's name)
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Patient').fill('Demoson');
    await dialog
      .getByRole('button', { name: /Demoson, Emma \(/ })
      .first()
      .click();
    await dialog.getByLabel('Provider').selectOption({ index: 1 });
    await dialog.getByLabel('Operatory').selectOption({ index: 1 });

    // far-future Mon-Fri slot inside seeded provider hours (09:00-17:00) so
    // availability templates never reject it; random time reduces collisions
    // with blocks left behind by previous runs of this test
    const future = new Date();
    future.setDate(future.getDate() + 60 + Math.floor(Math.random() * 200));
    while (future.getDay() === 0 || future.getDay() === 6) {
      future.setDate(future.getDate() + 1);
    }
    const iso = future.toISOString().slice(0, 10);
    const hour = 9 + Math.floor(Math.random() * 7);
    const minute = [0, 15, 30][Math.floor(Math.random() * 3)];
    const start = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
    await dialog.getByLabel('Date').fill(iso);
    await dialog.getByLabel('Start').fill(start);
    const createdResponse = page.waitForResponse(
      (r) => r.url().includes('/api/v1/appointments') && r.request().method() === 'POST',
    );
    await dialog.getByRole('button', { name: 'Book appointment' }).click();
    const appointmentId = ((await (await createdResponse).json()) as { id: string }).id;
    // a rejected booking (409) keeps the dialog open — fail here, not downstream
    await expect(dialog).toBeHidden();

    // deep-link to the exact appointment: clicking by patient title is ambiguous
    // because earlier runs leave their own Demoson blocks in random weeks
    await page.goto(`/schedule?date=${iso}&appointment=${appointmentId}`);
    await expect(page.getByText('Scheduled', { exact: true })).toBeVisible();

    // walk-in check-in
    await page.getByRole('button', { name: 'Checked in' }).click();
    await expect(page.getByText('Checked in', { exact: true }).first()).toBeVisible();
  });

  test('read-only user sees data but no write controls', async ({ page, request }) => {
    // ensure a read-only user exists (idempotent via API)
    const loginResponse = await request.post('/api/v1/auth/login', {
      data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
    });
    const { accessToken } = await loginResponse.json();
    await request.post('/api/v1/users', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        email: 'e2e-readonly@clinic.test',
        password: 'readonly-pass-12',
        firstName: 'Read',
        lastName: 'Only',
        roles: ['READ_ONLY'],
      },
    }); // 201 or 409 — both fine

    await page.goto('/login');
    await page.getByLabel('Email').fill('e2e-readonly@clinic.test');
    await page.getByLabel('Password').fill('readonly-pass-12');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible();

    await nav(page, 'Patients').click();
    await expect(page.getByRole('heading', { name: 'Patients' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'New patient' })).toHaveCount(0);

    await nav(page, 'Schedule').click();
    await expect(page.getByRole('button', { name: 'New appointment' })).toHaveCount(0);
  });
});
