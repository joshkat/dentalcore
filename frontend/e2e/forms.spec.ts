import { expect, test, type Page } from '@playwright/test';

/**
 * Phase D — patient forms, e-signature and note templates, against the full
 * running stack. Follows the checkout/clinical spec patterns: admin login,
 * unique names via Date.now() (suite runs with workers:1 on a shared dev DB).
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

/** Register a fresh patient so document/form assertions are isolated. */
async function registerPatient(page: Page, lastName: string) {
  await nav(page, 'Patients').click();
  await page.getByRole('button', { name: 'New patient' }).click();
  await page.getByLabel('First name').fill('Fern');
  await page.getByLabel('Last name').fill(lastName);
  await page.getByLabel('Date of birth').fill('1992-03-03');
  await page.getByLabel('Number').fill('555-444-1212');
  await page.getByRole('button', { name: 'Register patient' }).click();
  await expect(page.getByRole('heading', { name: new RegExp(lastName) })).toBeVisible();
}

/** Draw a small zig-zag on the signature canvas with the mouse. */
async function drawSignature(page: Page) {
  const canvas = page.getByTestId('signature-canvas');
  await expect(canvas).toBeVisible();
  const box = (await canvas.boundingBox())!;
  await page.mouse.move(box.x + 30, box.y + 60);
  await page.mouse.down();
  await page.mouse.move(box.x + 90, box.y + 40, { steps: 5 });
  await page.mouse.move(box.x + 150, box.y + 80, { steps: 5 });
  await page.mouse.move(box.x + 210, box.y + 50, { steps: 5 });
  await page.mouse.up();
}

test.describe('forms & e-signature', () => {
  test('admin builds a template, patient form is filled, signed, and the PDF lands in Documents', async ({
    page,
  }) => {
    const stamp = Date.now();
    const templateName = `E2E Intake ${stamp}`;

    await login(page);

    // --- build a 3-field template (text required, date, checkbox) ---
    await nav(page, 'Forms').click();
    await expect(page.getByRole('heading', { name: 'Forms' })).toBeVisible();
    await page.getByRole('button', { name: 'New template' }).click();

    const builder = page.getByRole('dialog', { name: 'New form template' });
    await builder.getByLabel('Template name').fill(templateName);

    await builder.getByLabel('Field 1 label').fill('Full name');
    // TEXT is the default type; mark it required
    await builder.getByLabel('Required').first().check();

    await builder.getByRole('button', { name: 'Add field' }).click();
    await builder.getByLabel('Field 2 label').fill('Visit date');
    await builder.getByLabel('Type').nth(1).selectOption('DATE');

    await builder.getByRole('button', { name: 'Add field' }).click();
    await builder.getByLabel('Field 3 label').fill('Consent given');
    await builder.getByLabel('Type').nth(2).selectOption('CHECKBOX');

    await builder.getByRole('button', { name: 'Create template' }).click();
    await expect(page.getByText(templateName)).toBeVisible();
    await expect(
      page.getByRole('row', { name: new RegExp(templateName) }).getByText('3 fields'),
    ).toBeVisible();

    // --- a Demoson patient fills the form ---
    const lastName = `FormsDemoson${stamp}`;
    await registerPatient(page, lastName);

    await page.getByRole('button', { name: 'Forms', exact: true }).click();
    await page.getByRole('button', { name: 'New form' }).click();
    const picker = page.getByRole('dialog', { name: 'New form' });
    await picker.getByLabel('Template').selectOption({ label: templateName });
    await picker.getByRole('button', { name: 'Start form' }).click();

    // fill view opens in DRAFT
    await expect(page.getByText('DRAFT', { exact: true })).toBeVisible();
    await page.getByLabel('Full name').fill('Fern Demoson');
    await page.getByLabel('Visit date').fill('2026-06-12');
    await page.getByLabel('Consent given').check();
    // blur the focused field so the autosave PUT fires
    await page.getByLabel('Full name').blur();

    // server flips DRAFT -> COMPLETED once required answers exist; sign panel appears
    await expect(page.getByText('COMPLETED', { exact: true })).toBeVisible();
    await expect(page.getByText('Sign this form')).toBeVisible();

    // --- e-signature: name + canvas drawing ---
    await page.getByLabel('Signed by (full name)').fill('Fern Demoson');
    const signButton = page.getByRole('button', { name: 'Sign', exact: true });
    await expect(signButton).toBeDisabled();
    await drawSignature(page);
    await expect(signButton).toBeEnabled();
    await signButton.click();

    await expect(page.getByText('SIGNED', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'View PDF' })).toBeVisible();

    // list view shows the SIGNED badge too
    await page.getByRole('button', { name: '← All forms' }).click();
    await expect(page.getByText(templateName)).toBeVisible();
    await expect(page.getByText('SIGNED', { exact: true })).toBeVisible();

    // --- the generated PDF appears in the patient's Documents tab ---
    await page.getByRole('button', { name: 'Documents', exact: true }).click();
    // fresh patient: the only document is the signed-form PDF
    await expect(page.getByText(/\.pdf/i).first()).toBeVisible();
    await expect(page.getByText('No documents on file.')).not.toBeVisible();
  });

  test('note template with {{tooth}} interpolates into a clinical note', async ({ page }) => {
    const stamp = Date.now();
    const templateName = `E2E Tmpl ${stamp}`;
    const lastName = `NotesDemoson${stamp}`;

    await login(page);
    await registerPatient(page, lastName);

    await page.getByRole('button', { name: 'Notes', exact: true }).click();

    // --- create the note template inline ---
    await page.getByRole('button', { name: 'Manage templates' }).click();
    const manager = page.getByRole('dialog', { name: 'Note templates' });
    await manager.getByRole('button', { name: 'New note template' }).click();
    await manager.getByLabel('Template name').fill(templateName);
    await manager.getByLabel('Note type').selectOption('PROGRESS');
    await manager
      .getByLabel(/Body/)
      .fill('Tooth {{tooth}} restored with composite. No complications.');
    // detected prompts preview
    await expect(manager.getByText('Prompts: tooth')).toBeVisible();
    await manager.getByRole('button', { name: 'Create template' }).click();
    await expect(manager.getByText(templateName)).toBeVisible();
    await manager.getByRole('button', { name: 'Close' }).click();

    // --- use it in the composer ---
    await page.getByRole('button', { name: 'New clinical note' }).click();
    await page.getByLabel('Use template').selectOption({ label: templateName });
    await page.getByLabel('tooth', { exact: true }).fill('14');
    await page.getByRole('button', { name: 'Insert' }).click();

    await expect(page.getByLabel('Note')).toHaveValue(
      'Tooth 14 restored with composite. No complications.',
    );
    await page.getByRole('button', { name: 'Save note' }).click();
    await expect(
      page.getByText('Tooth 14 restored with composite. No complications.'),
    ).toBeVisible();
  });
});
