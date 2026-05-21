import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  slugifyKey,
  TemplateBuilderModal,
  validateTemplateDraft,
  type FieldDraft,
} from './TemplateBuilderModal';

const apiMock = vi.fn();

vi.mock('../../lib/api', () => ({
  api: (...args: unknown[]) => apiMock(...args),
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, problem: { detail?: string } | null) {
      super(problem?.detail ?? `Request failed (${status})`);
      this.status = status;
    }
  },
  getAccessToken: () => null,
  setAccessToken: () => undefined,
  refreshSession: async () => null,
}));

function renderModal() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <TemplateBuilderModal template={null} onClose={vi.fn()} />
    </QueryClientProvider>,
  );
}

const field = (overrides: Partial<FieldDraft>): FieldDraft => ({
  label: 'Field',
  type: 'TEXT',
  required: false,
  options: '',
  ...overrides,
});

describe('validateTemplateDraft', () => {
  it('requires SELECT fields to define options', () => {
    expect(
      validateTemplateDraft('Intake', [field({ label: 'Smoker?', type: 'SELECT', options: '' })]),
    ).toMatch(/needs at least one option/);
    expect(
      validateTemplateDraft('Intake', [
        field({ label: 'Smoker?', type: 'SELECT', options: 'Yes, No' }),
      ]),
    ).toBeNull();
  });

  it('rejects labels that collapse to duplicate keys', () => {
    expect(
      validateTemplateDraft('Intake', [
        field({ label: 'Full Name' }),
        field({ label: 'full  name!' }), // same slug: full_name
      ]),
    ).toMatch(/Duplicate field keys/);
  });

  it('slugifies labels into stable keys', () => {
    expect(slugifyKey('Full  Name!')).toBe('full_name');
    expect(slugifyKey('  Date of Birth ')).toBe('date_of_birth');
  });
});

describe('TemplateBuilderModal', () => {
  beforeEach(() => {
    apiMock.mockReset();
  });

  it('blocks saving a SELECT field without options and never calls the api', async () => {
    renderModal();
    await userEvent.type(screen.getByLabelText('Template name'), 'Intake');
    await userEvent.type(screen.getByLabelText('Field 1 label'), 'Smoker');
    await userEvent.selectOptions(screen.getByLabelText('Type'), 'SELECT');
    await userEvent.click(screen.getByRole('button', { name: 'Create template' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/needs at least one option/);
    expect(apiMock).not.toHaveBeenCalled();
  });

  it('flags duplicate labels as duplicate keys', async () => {
    renderModal();
    await userEvent.type(screen.getByLabelText('Template name'), 'Intake');
    await userEvent.type(screen.getByLabelText('Field 1 label'), 'Full Name');
    await userEvent.click(screen.getByRole('button', { name: 'Add field' }));
    await userEvent.type(screen.getByLabelText('Field 2 label'), 'Full name');
    await userEvent.click(screen.getByRole('button', { name: 'Create template' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Duplicate field keys/);
    expect(apiMock).not.toHaveBeenCalled();
  });

  it('posts slugified keys and parsed options for a valid template', async () => {
    apiMock.mockResolvedValue({});
    renderModal();
    await userEvent.type(screen.getByLabelText('Template name'), 'Intake');
    await userEvent.type(screen.getByLabelText('Field 1 label'), 'Smoking status');
    await userEvent.selectOptions(screen.getByLabelText('Type'), 'SELECT');
    await userEvent.type(
      screen.getByLabelText('Field 1 options (comma-separated)'),
      'Never, Former, Current',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Create template' }));

    expect(apiMock).toHaveBeenCalledWith('/api/v1/forms/templates', {
      method: 'POST',
      body: {
        name: 'Intake',
        description: undefined,
        fields: [
          {
            key: 'smoking_status',
            label: 'Smoking status',
            type: 'SELECT',
            required: false,
            options: ['Never', 'Former', 'Current'],
          },
        ],
      },
    });
  });
});
