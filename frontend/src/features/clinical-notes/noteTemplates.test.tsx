import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ClinicalNotesTab } from './ClinicalNotesTab';
import { extractPrompts, interpolateTemplate } from './noteTemplates';

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

const TEMPLATE = {
  id: 'tmpl-1',
  name: 'Composite restoration',
  noteType: 'PROGRESS',
  body: 'Tooth {{tooth}} restored with composite, shade {{shade}}.',
  prompts: ['tooth', 'shade'],
  createdAt: '2026-06-12T08:00:00.000Z',
  updatedAt: '2026-06-12T08:00:00.000Z',
};

function mockRoutes() {
  apiMock.mockImplementation(async (path: string) => {
    if (path.startsWith('/api/v1/clinical-notes/templates')) return [TEMPLATE];
    if (path.startsWith('/api/v1/clinical-notes')) {
      return { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };
    }
    throw new Error(`Unhandled request: ${path}`);
  });
}

function renderTab() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ClinicalNotesTab patientId="pat-1" canWriteClinical />
    </QueryClientProvider>,
  );
}

describe('interpolateTemplate / extractPrompts', () => {
  it('extracts unique placeholder keys in order', () => {
    expect(extractPrompts('{{tooth}} and {{shade}} and {{tooth}}')).toEqual(['tooth', 'shade']);
  });

  it('replaces filled values and keeps blank placeholders visible', () => {
    expect(interpolateTemplate('Tooth {{tooth}}, shade {{shade}}', { tooth: '14' })).toBe(
      'Tooth 14, shade {{shade}}',
    );
  });
});

describe('ClinicalNotesTab auto-notes', () => {
  beforeEach(() => {
    apiMock.mockReset();
    mockRoutes();
  });

  it('renders a prompt input for {{tooth}} and inserts the interpolated body', async () => {
    renderTab();
    await userEvent.click(await screen.findByRole('button', { name: 'New clinical note' }));

    await userEvent.selectOptions(await screen.findByLabelText('Use template'), 'tmpl-1');

    // prompt inputs appear for each placeholder
    const toothInput = await screen.findByLabelText('tooth');
    await userEvent.type(toothInput, '14');
    await userEvent.type(screen.getByLabelText('shade'), 'A2');

    await userEvent.click(screen.getByRole('button', { name: 'Insert' }));
    expect(screen.getByLabelText('Note')).toHaveValue(
      'Tooth 14 restored with composite, shade A2.',
    );
  });

  it('appends below existing body text instead of clobbering it', async () => {
    renderTab();
    await userEvent.click(await screen.findByRole('button', { name: 'New clinical note' }));
    await userEvent.type(screen.getByLabelText('Note'), 'Existing line.');

    await userEvent.selectOptions(await screen.findByLabelText('Use template'), 'tmpl-1');
    await userEvent.type(await screen.findByLabelText('tooth'), '30');
    await userEvent.click(screen.getByRole('button', { name: 'Insert' }));

    expect(screen.getByLabelText('Note')).toHaveValue(
      'Existing line.\nTooth 30 restored with composite, shade {{shade}}.',
    );
  });
});
