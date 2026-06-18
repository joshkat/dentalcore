import { expect, test, type Page } from '@playwright/test';

/**
 * Phase C — Permissions & Admin. Requires the full stack running
 * (docker compose up) with demo data and an ADMIN login.
 *
 * The permission-matrix test mutates FRONT_DESK's grants and restores them
 * before finishing, so reruns against a shared dev DB stay clean.
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

test.describe('admin page', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await nav(page, 'Admin').click();
    await expect(page.getByRole('heading', { name: 'Admin', exact: true })).toBeVisible();
  });

  test('permission matrix renders all six role columns and the permission rows', async ({
    page,
  }) => {
    const matrix = page.getByRole('region', { name: 'Permission matrix' });
    await expect(matrix.getByText('Changes apply immediately to active sessions.')).toBeVisible();

    // column headers show the localized role display names
    for (const role of ['Administrator', 'Dentist', 'Hygienist', 'Front Desk', 'Billing', 'Read Only']) {
      await expect(matrix.getByRole('columnheader', { name: role, exact: true })).toBeVisible();
    }

    // one checkbox per permission per role → rows = total / 6
    const checkboxes = matrix.locator('input[type=checkbox]');
    expect((await checkboxes.count()) / 6).toBeGreaterThanOrEqual(20);

    // ADMIN can never lose the admin-management permissions
    await expect(
      matrix.getByRole('checkbox', { name: 'PERMISSIONS_MANAGE ADMIN' }),
    ).toBeDisabled();
    await expect(matrix.getByRole('checkbox', { name: 'USERS_MANAGE ADMIN' })).toBeDisabled();
  });

  test('toggling a FRONT_DESK permission saves, then restores the original state', async ({
    page,
  }) => {
    const matrix = page.getByRole('region', { name: 'Permission matrix' });
    // pick a currently-granted FRONT_DESK permission so we toggle off → save → on → save
    const granted = matrix.locator(
      'input[type=checkbox][data-role="FRONT_DESK"]:checked:not([disabled])',
    );
    await expect(granted.first()).toBeVisible();
    const checkbox = granted.first();
    const code = await checkbox.getAttribute('data-code');
    expect(code).toBeTruthy();
    const exact = matrix.getByRole('checkbox', { name: `${code} FRONT_DESK` });

    const saveResponse = () =>
      page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/admin/roles/FRONT_DESK/permissions') &&
          r.request().method() === 'PUT' &&
          r.ok(),
      );

    // toggle off + save
    await exact.uncheck();
    let responded = saveResponse();
    await matrix.getByRole('button', { name: 'Save FRONT_DESK' }).click();
    await responded;
    await expect(matrix.getByRole('button', { name: 'Save FRONT_DESK' })).toBeHidden();
    await expect(exact).not.toBeChecked();

    // toggle back on + save — leave the state exactly as we found it
    await exact.check();
    responded = saveResponse();
    await matrix.getByRole('button', { name: 'Save FRONT_DESK' }).click();
    await responded;
    await expect(matrix.getByRole('button', { name: 'Save FRONT_DESK' })).toBeHidden();
    await expect(exact).toBeChecked();
  });

  test('duplicate patients section renders (candidates or empty state)', async ({ page }) => {
    const duplicates = page.getByRole('region', { name: 'Duplicate patients' });
    await expect(
      duplicates.getByRole('heading', { name: 'Duplicate patients' }),
    ).toBeVisible();
    await expect(
      duplicates
        .getByRole('button', { name: 'Merge…' })
        .first()
        .or(duplicates.getByText('No duplicate candidates found.')),
    ).toBeVisible();
  });
});

test.describe('statement runs report', () => {
  test('generates a run with a sky-high min balance and shows it in history', async ({
    page,
  }) => {
    await login(page);
    await nav(page, 'Reports').click();
    await page.getByLabel('Report', { exact: true }).selectOption('Statement runs');

    await expect(page.getByRole('heading', { name: 'Statement runs' })).toBeVisible();

    // min balance nobody owes → run completes with (almost certainly) 0 accounts
    await page.getByLabel('Min balance').fill('999999');
    page.once('dialog', (dialog) => void dialog.accept());
    const created = page.waitForResponse(
      (r) =>
        r.url().includes('/api/v1/billing/statement-runs') &&
        r.request().method() === 'POST' &&
        r.status() === 201,
    );
    await page.getByRole('button', { name: 'Generate statements' }).click();
    const run = (await (await created).json()) as { id: string; totalAccounts: number };

    // success banner + the new run visible in the history table
    await expect(page.getByTestId('stmt-run-created')).toContainText(
      `Generated ${run.totalAccounts} statement`,
    );
    const history = page.getByTestId('stmt-run-history');
    await expect(history).toBeVisible();
    await expect(history.getByText('$999,999.00').first()).toBeVisible();
  });
});
