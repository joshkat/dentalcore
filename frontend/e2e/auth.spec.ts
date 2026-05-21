import { expect, test } from '@playwright/test';

/**
 * E2E auth flow. Requires the full stack running (docker compose up) with the
 * bootstrapped admin: ADMIN_EMAIL / ADMIN_PASSWORD from .env
 * (defaults: admin@dentalcore.local / see .env.example).
 */
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@dentalcore.local';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'change-me-admin-1';

test.describe('authentication', () => {
  test('redirects anonymous visitors to login', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByLabel('Email')).toBeVisible();
  });

  test('rejects invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(ADMIN_EMAIL);
    await page.getByLabel('Password').fill('definitely-wrong-1');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('alert')).toContainText('Invalid credentials');
  });

  test('signs in, shows dashboard, session survives reload, signs out', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(ADMIN_EMAIL);
    await page.getByLabel('Password').fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible();

    // Session restored from the httpOnly refresh cookie.
    await page.reload();
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible();

    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login/);
  });

  test('admin can manage users', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(ADMIN_EMAIL);
    await page.getByLabel('Password').fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible();

    await page.getByRole('link', { name: 'Users' }).click();
    await expect(page.getByRole('heading', { name: 'Users' })).toBeVisible();

    const email = `e2e-${Date.now()}@clinic.test`;
    await page.getByRole('button', { name: 'New user' }).click();
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('First name').fill('End');
    await page.getByLabel('Last name').fill('ToEnd');
    await page.getByLabel('Temporary password').fill('e2e-password-12');
    await page.getByLabel('FRONT DESK').check();
    await page.getByRole('button', { name: 'Create user' }).click();

    // the list paginates by lastName and e2e users accumulate in the dev DB,
    // so locate the new user via search instead of expecting them on page 1
    await page.getByLabel('Search users').fill(email);
    await expect(page.getByText(email)).toBeVisible();
  });
});
