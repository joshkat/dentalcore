import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Appointment } from '../../types/api';
import { CheckoutModal } from './CheckoutModal';

const apiMock = vi.fn();

vi.mock('../../lib/api', () => ({
  api: (...args: unknown[]) => apiMock(...args),
  ApiError: class ApiError extends Error {
    status: number;
    problem: unknown;
    constructor(status: number, problem: { detail?: string } | null) {
      super(problem?.detail ?? `Request failed (${status})`);
      this.status = status;
      this.problem = problem;
    }
  },
  getAccessToken: () => null,
  setAccessToken: () => undefined,
  refreshSession: async () => null,
}));

const appointment: Appointment = {
  id: 'appt-1',
  patientId: 'pat-1',
  patientFirstName: 'Emma',
  patientLastName: 'Demoson',
  providerId: 'prov-1',
  providerFirstName: 'Dana',
  providerLastName: 'Drill',
  operatoryId: 'op-1',
  operatoryName: 'Op 1',
  startsAt: '2026-06-12T15:00:00.000Z',
  endsAt: '2026-06-12T16:00:00.000Z',
  status: 'CHECKED_IN',
  asap: false,
  notes: null,
  color: '#3b82f6',
  cancelledReason: null,
  procedures: [
    {
      procedureCodeId: 'code-1',
      code: 'D1110',
      description: 'Prophylaxis — adult',
      standardFee: 100,
    },
  ],
  createdAt: '2026-06-12T08:00:00.000Z',
  updatedAt: '2026-06-12T08:00:00.000Z',
};

const NO_COVERAGE_ESTIMATE = {
  hasCoverage: false,
  carrierName: null,
  planName: null,
  totalInsurance: 0,
  totalPatient: 0,
  totalWriteOff: 0,
  lines: [],
};

const PPO_ESTIMATE = {
  hasCoverage: true,
  carrierName: 'Delta Dental',
  planName: 'PPO 100',
  totalInsurance: 60,
  totalPatient: 40,
  totalWriteOff: 0,
  lines: [],
};

/** Stateful route table for everything the checkout panel touches. */
function mockApiRoutes(estimate: Record<string, unknown>) {
  const completedStore: Array<Record<string, unknown>> = [];
  apiMock.mockImplementation(
    async (path: string, options: { method?: string; body?: Record<string, unknown> } = {}) => {
      const method = options.method ?? 'GET';
      if (method === 'GET' && path.startsWith('/api/v1/completed-procedures')) {
        return [...completedStore];
      }
      if (method === 'POST' && path === '/api/v1/completed-procedures') {
        const body = options.body!;
        const created = {
          id: `cp-${completedStore.length + 1}`,
          patientId: body.patientId,
          providerId: body.providerId,
          providerFirstName: 'Dana',
          providerLastName: 'Drill',
          procedureCodeId: body.procedureCodeId,
          code: 'D1110',
          description: 'Prophylaxis — adult',
          tooth: body.tooth ?? null,
          surfaces: body.surfaces ?? null,
          fee: (body.feeOverride as number | undefined) ?? 100,
          appointmentId: body.appointmentId ?? null,
          plannedProcedureId: body.plannedProcedureId ?? null,
          completedAt: '2026-06-12T15:30:00.000Z',
          entryDate: '2026-06-12',
        };
        completedStore.push(created);
        return created;
      }
      if (method === 'POST' && path === '/api/v1/insurance/estimate') return estimate;
      if (method === 'GET' && path.startsWith('/api/v1/patients/')) {
        return { id: 'pat-1', nextRecallDate: null };
      }
      if (method === 'POST' && path === '/api/v1/billing/payments') return { id: 'pay-1' };
      if (method === 'PATCH' && path.startsWith('/api/v1/appointments/')) {
        return { ...appointment, status: 'COMPLETED' };
      }
      throw new Error(`Unhandled request: ${method} ${path}`);
    },
  );
}

function renderModal() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <CheckoutModal appointment={appointment} onClose={vi.fn()} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('CheckoutModal', () => {
  beforeEach(() => {
    apiMock.mockReset();
  });

  it('completing a procedure row posts with the appointment context and flips the row', async () => {
    mockApiRoutes(NO_COVERAGE_ESTIMATE);
    renderModal();

    expect(await screen.findByText('Prophylaxis — adult')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Complete' }));

    await waitFor(() =>
      expect(apiMock).toHaveBeenCalledWith(
        '/api/v1/completed-procedures',
        expect.objectContaining({
          method: 'POST',
          body: expect.objectContaining({
            patientId: 'pat-1',
            providerId: 'prov-1',
            procedureCodeId: 'code-1',
            appointmentId: 'appt-1',
            feeOverride: 100,
          }),
        }),
      ),
    );

    // Row flips to the completed state: Undo appears, the Complete button is gone.
    expect(await screen.findByRole('button', { name: 'Undo' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Complete' })).not.toBeInTheDocument();

    // Without coverage the payment defaults to the completed fees total.
    await waitFor(() => expect(screen.getByLabelText('Amount ($)')).toHaveValue(100));
  });

  it('defaults the payment amount to the estimated patient portion when covered', async () => {
    mockApiRoutes(PPO_ESTIMATE);
    renderModal();

    await userEvent.click(await screen.findByRole('button', { name: 'Complete' }));
    expect(await screen.findByRole('button', { name: 'Undo' })).toBeInTheDocument();

    // Estimate summary appears…
    expect(await screen.findByText(/Est\. patient portion/)).toBeInTheDocument();
    expect(screen.getByText('$60.00')).toBeInTheDocument();

    // …and the payment amount follows estimate.totalPatient, not the gross fee.
    await waitFor(() => expect(screen.getByLabelText('Amount ($)')).toHaveValue(40));
  });
});
