import { expect, test, type Page } from '@playwright/test';

/**
 * Phase B — family billing & RCM (requires the full stack running):
 * set a guarantor between the two seeded Demoson patients, post a charge,
 * verify the combined family ledger, create a payment plan, then check the
 * A/R aging and Collections reports render.
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

function nav(page: Page, name: string) {
  return page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('link', { name, exact: true });
}

test('family billing: guarantor, family ledger, payment plan, A/R reports', async ({
  page,
}) => {
  await login(page);

  // ---- 1. Open Liam Demoson and make Emma Demoson his guarantor ----
  await nav(page, 'Patients').click();
  await page.getByLabel('Search patients').fill('Demoson');
  await page.getByRole('link', { name: /Demoson, Liam/ }).click();
  await expect(page.getByRole('heading', { name: /Demoson, Liam/ })).toBeVisible();

  await page.getByRole('button', { name: 'Family', exact: true }).click();
  await page.getByRole('button', { name: 'Change guarantor' }).click();
  await page.getByLabel('Find guarantor').fill('Demoson');
  await page.getByRole('button', { name: /Demoson, Emma \(/ }).click();

  // Guarantor now links to Emma (idempotent across re-runs).
  await expect(
    page.getByRole('link', { name: /Demoson, Emma/ }).first(),
  ).toBeVisible();

  // ---- 2. Post a charge so the family ledger has activity ----
  await page.getByRole('button', { name: 'Ledger', exact: true }).click();
  await expect(page.getByText('Account balance')).toBeVisible();

  const chargeStamp = `E2E family charge ${Date.now().toString().slice(-7)}`;
  await page.getByRole('button', { name: 'Add charge' }).click();
  await page.getByLabel('Amount ($)', { exact: true }).fill('45');
  await page.getByLabel('Description', { exact: true }).fill(chargeStamp);
  await page.getByRole('button', { name: 'Post charge' }).click();
  await expect(page.getByText(chargeStamp)).toBeVisible();

  // ---- 3. Family view shows both members and the combined entries ----
  await page.getByRole('button', { name: 'Family view' }).click();
  const members = page.getByTestId('family-members');
  await expect(members).toContainText('Demoson, Emma');
  await expect(members).toContainText('Demoson, Liam');
  await expect(page.getByTestId('family-total-balance')).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Family statement (PDF)' }),
  ).toBeVisible();
  await expect(page.getByText(chargeStamp)).toBeVisible();
  await page.getByRole('button', { name: 'Patient view' }).click();

  // ---- 4. Create a payment plan and see it listed ----
  await page.getByRole('button', { name: 'New payment plan' }).click();
  const planDialog = page.getByRole('dialog', { name: 'New payment plan' });
  await planDialog.getByLabel('Total amount ($)').fill('100');
  await planDialog.getByLabel('Installment ($)').fill('25');
  await expect(planDialog.getByTestId('installment-preview')).toContainText(
    '4 monthly installments',
  );
  await planDialog.getByRole('button', { name: 'Create plan' }).click();
  await expect(planDialog).toBeHidden();
  await expect(page.getByText('ACTIVE').first()).toBeVisible();
  await expect(page.getByText(/received of/).first()).toBeVisible();

  // ---- 5. Reports: A/R aging buckets render; Collections renders ----
  await nav(page, 'Reports').click();
  await page.getByLabel('Report', { exact: true }).selectOption('A/R aging');
  await expect(page.getByTestId('ar-bucket-total')).toBeVisible();
  await expect(page.getByTestId('ar-bucket-current')).toBeVisible();
  await expect(page.getByTestId('ar-bucket-days90plus')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Print' })).toBeVisible();

  await page.getByLabel('Report', { exact: true }).selectOption('Collections');
  await expect(
    page
      .getByTestId('collections-table')
      .or(page.getByText('No overdue accounts. Nice work.'))
      .first(),
  ).toBeVisible();
});
