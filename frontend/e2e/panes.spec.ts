import { expect, test, type Page } from '@playwright/test';

/**
 * Split-pane workspace: drag sidebar items into the main area for tmux-style
 * splits. Requires the full stack running (docker compose up) with demo data.
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

/** HTML5 drag from a sidebar item onto a pane at a relative position. */
async function dragNavToPane(
  page: Page,
  navLabel: string,
  paneSelector: string,
  rx: number,
  ry: number,
) {
  const source = nav(page).getByRole('link', { name: navLabel });
  const sourceBox = (await source.boundingBox())!;
  await page.mouse.move(sourceBox.x + sourceBox.width / 2, sourceBox.y + sourceBox.height / 2);
  await page.mouse.down();
  const target = page.locator(paneSelector);
  const box = (await target.boundingBox())!;
  const x = box.x + box.width * rx;
  const y = box.y + box.height * ry;
  await page.mouse.move(x, y, { steps: 10 });
  await page.mouse.move(x, y + 1);
  await page.mouse.up();
}

test.describe('split-pane workspace', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.evaluate(() => localStorage.removeItem('dentalcore.panes'));
    await page.reload();
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible();
  });

  test('drag to right edge splits, bottom corner makes a 3-way, panes close and persist', async ({
    page,
  }) => {
    // 1. Drag Patients to the right edge of the main view → 2-way split.
    await dragNavToPane(page, 'Patients', 'section[data-pane="primary"]', 0.95, 0.5);
    const patientsPane = page.locator('section[aria-label="Pane: Patients"]');
    await expect(patientsPane).toBeVisible();
    await expect(patientsPane.getByRole('heading', { name: 'Patients' })).toBeVisible();
    // Main pane keeps the dashboard and the browser URL is untouched.
    await expect(page.locator('section[data-pane="primary"]')).toContainText(/welcome back/i);
    await expect(page).toHaveURL('/');

    // 2. Drag Schedule to the bottom of the Patients pane → 3-way split.
    await dragNavToPane(page, 'Schedule', 'section[aria-label="Pane: Patients"]', 0.5, 0.9);
    const schedulePane = page.locator('section[aria-label="Pane: Schedule"]');
    await expect(schedulePane).toBeVisible();
    await expect(page.locator('section[data-pane]')).toHaveCount(3);

    // No horizontal overflow, even with the schedule grid in a split.
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);

    // 3. Navigation inside a pane stays inside that pane.
    await patientsPane.getByRole('link', { name: /Demoson/ }).first().click();
    await expect(page.locator('section[aria-label="Pane: Patient"]')).toBeVisible();
    await expect(page).toHaveURL('/');

    // 4. Layout survives a reload.
    await page.reload();
    await expect(page.locator('section[data-pane]')).toHaveCount(3);
    await expect(page.locator('section[aria-label="Pane: Patient"]')).toBeVisible();

    // 5. Closing panes returns to the single full-screen view.
    while ((await page.getByRole('button', { name: 'Close pane' }).count()) > 0) {
      await page.getByRole('button', { name: 'Close pane' }).first().click();
    }
    await expect(page.locator('section[data-pane]')).toHaveCount(1);
    await expect(page.locator('section[data-pane="primary"] header')).toHaveCount(0);
  });

  test('sidebar collapses and expands, state persists', async ({ page }) => {
    await expect(nav(page)).toBeVisible();
    await page.getByRole('button', { name: 'Collapse sidebar' }).click();
    await expect(nav(page)).toHaveCount(0);

    await page.reload();
    await expect(page.getByRole('button', { name: 'Expand sidebar' })).toBeVisible();
    await expect(nav(page)).toHaveCount(0);

    await page.getByRole('button', { name: 'Expand sidebar' }).click();
    await expect(nav(page)).toBeVisible();
  });
});
