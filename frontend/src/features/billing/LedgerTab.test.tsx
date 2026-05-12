import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LedgerTab } from './LedgerTab';

const apiMock = vi.fn();

vi.mock('../../lib/api', () => ({
  api: (...args: unknown[]) => apiMock(...args),
  getAccessToken: () => null,
  refreshSession: async () => null,
  setAccessToken: () => undefined,
  ApiError: class ApiError extends Error {
    status: number;
    problem: { detail?: string } | null;
    constructor(status: number, problem: { detail?: string } | null) {
      super(problem?.detail ?? `Request failed (${status})`);
      this.status = status;
      this.problem = problem;
    }
  },
}));

vi.mock('../../lib/auth', () => ({
  useAuth: () => ({
    user: { id: 'u1', email: 'a@b.c', firstName: 'Ada', lastName: 'Admin', roles: ['ADMIN'] },
    initializing: false,
    login: vi.fn(),
    logout: vi.fn(),
    hasRole: () => true,
  }),
}));

const ledgerEntry = {
  id: 'le-1',
  type: 'CHARGE',
  amount: 75,
  description: 'Prophylaxis',
  method: null,
  procedureCodeId: null,
  procedureCode: null,
  appointmentId: null,
  claimId: null,
  entryDate: '2026-06-01',
  reversalOf: null,
  reversed: false,
  createdAt: '2026-06-01T10:00:00Z',
};

// Per-test switches for the mocked backend.
const state = {
  guarantorId: null as string | null,
  familyMembers: [
    { patientId: 'pat-1', patientName: 'Demoson, Liam', balance: 75 },
    { patientId: 'guar-1', patientName: 'Demoson, Emma', balance: 100 },
  ],
};

function installRoutes() {
  apiMock.mockImplementation(async (path: string) => {
    if (path.startsWith('/api/v1/billing/ledger')) {
      return {
        balance: 75,
        content: [ledgerEntry],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      };
    }
    if (path.startsWith('/api/v1/patients/')) {
      return {
        id: 'pat-1',
        firstName: 'Liam',
        lastName: 'Demoson',
        guarantorId: state.guarantorId,
        guarantorFirstName: state.guarantorId ? 'Emma' : null,
        guarantorLastName: state.guarantorId ? 'Demoson' : null,
      };
    }
    if (path.startsWith('/api/v1/billing/family-ledger')) {
      return {
        guarantorId: state.guarantorId ?? 'pat-1',
        guarantorName: state.guarantorId ? 'Demoson, Emma' : 'Demoson, Liam',
        members: state.familyMembers,
        entries: [
          { ...ledgerEntry, patientId: 'pat-1', patientName: 'Demoson, Liam' },
          {
            ...ledgerEntry,
            id: 'le-2',
            amount: 100,
            description: 'Crown',
            patientId: 'guar-1',
            patientName: 'Demoson, Emma',
          },
        ],
        totalBalance: 175,
      };
    }
    if (path.startsWith('/api/v1/billing/payment-plans')) {
      return [];
    }
    throw new Error(`Unhandled request: ${path}`);
  });
}

function renderTab() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LedgerTab patientId="pat-1" />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LedgerTab family view', () => {
  beforeEach(() => {
    apiMock.mockReset();
    state.guarantorId = 'guar-1';
    state.familyMembers = [
      { patientId: 'pat-1', patientName: 'Demoson, Liam', balance: 75 },
      { patientId: 'guar-1', patientName: 'Demoson, Emma', balance: 100 },
    ];
    installRoutes();
  });

  it('toggles to the combined family ledger with member chips and total', async () => {
    const user = userEvent.setup();
    renderTab();

    const toggle = await screen.findByRole('button', { name: 'Family view' });
    await user.click(toggle);

    // Member balance chips
    const members = screen.getByTestId('family-members');
    expect(members).toHaveTextContent('Demoson, Emma');
    expect(members).toHaveTextContent('Demoson, Liam (this patient)');

    // Combined entries carry a patient column and a family total
    expect(screen.getByRole('columnheader', { name: 'Patient' })).toBeInTheDocument();
    expect(screen.getByText('Crown')).toBeInTheDocument();
    expect(screen.getByTestId('family-total-balance')).toHaveTextContent('$175.00');
    expect(
      screen.getByRole('button', { name: 'Family statement (PDF)' }),
    ).toBeInTheDocument();

    // Toggle back restores the single-patient ledger
    await user.click(screen.getByRole('button', { name: 'Patient view' }));
    expect(screen.getByText('Account balance')).toBeInTheDocument();
  });

  it('hides the toggle when the patient has no guarantor and guarantees nobody', async () => {
    state.guarantorId = null;
    state.familyMembers = [
      { patientId: 'pat-1', patientName: 'Demoson, Liam', balance: 75 },
    ];
    renderTab();

    await screen.findByText('Account balance');
    await waitFor(() =>
      expect(
        apiMock.mock.calls.some(([path]) =>
          String(path).startsWith('/api/v1/billing/family-ledger'),
        ),
      ).toBe(true),
    );
    expect(screen.queryByRole('button', { name: 'Family view' })).not.toBeInTheDocument();
  });
});
