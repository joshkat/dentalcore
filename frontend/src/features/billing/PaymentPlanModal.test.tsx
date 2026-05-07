import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PaymentPlanModal } from './PaymentPlanModal';

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

function renderModal(onClose = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <PaymentPlanModal patientId="pat-1" open onClose={onClose} />
    </QueryClientProvider>,
  );
  return onClose;
}

describe('PaymentPlanModal', () => {
  beforeEach(() => {
    apiMock.mockReset();
  });

  it('shows a computed installment-count preview as amounts are entered', async () => {
    const user = userEvent.setup();
    renderModal();

    expect(screen.queryByTestId('installment-preview')).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('Total amount ($)'), '1200');
    await user.type(screen.getByLabelText('Down payment ($, optional)'), '200');
    await user.type(screen.getByLabelText('Installment ($)'), '100');

    const preview = screen.getByTestId('installment-preview');
    expect(preview).toHaveTextContent('10 monthly installments');
    expect(preview).toHaveTextContent('$100.00');

    // Uneven split surfaces the smaller final payment.
    await user.clear(screen.getByLabelText('Installment ($)'));
    await user.type(screen.getByLabelText('Installment ($)'), '300');
    expect(screen.getByTestId('installment-preview')).toHaveTextContent(
      '4 monthly installments',
    );
    expect(screen.getByTestId('installment-preview')).toHaveTextContent(
      'final payment $100.00',
    );
  });

  it('rejects an installment larger than the total without calling the API', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText('Total amount ($)'), '500');
    await user.type(screen.getByLabelText('Installment ($)'), '600');
    await user.click(screen.getByRole('button', { name: 'Create plan' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Installment cannot exceed the total',
    );
    expect(apiMock).not.toHaveBeenCalled();
  });

  it('rejects a down payment that covers the whole total', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText('Total amount ($)'), '500');
    await user.type(screen.getByLabelText('Down payment ($, optional)'), '500');
    await user.type(screen.getByLabelText('Installment ($)'), '100');
    await user.click(screen.getByRole('button', { name: 'Create plan' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Down payment must be less than the total',
    );
    expect(apiMock).not.toHaveBeenCalled();
  });

  it('posts the plan and closes on success', async () => {
    const user = userEvent.setup();
    apiMock.mockResolvedValueOnce({ id: 'plan-1' });
    const onClose = renderModal();

    await user.type(screen.getByLabelText('Total amount ($)'), '900');
    await user.type(screen.getByLabelText('Installment ($)'), '150');
    const dueDate = screen.getByLabelText('First due date');
    await user.clear(dueDate);
    await user.type(dueDate, '2026-07-01');
    await user.click(screen.getByRole('button', { name: 'Create plan' }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(apiMock).toHaveBeenCalledWith('/api/v1/billing/payment-plans', {
      method: 'POST',
      body: {
        patientId: 'pat-1',
        totalAmount: 900,
        downPayment: undefined,
        installmentAmount: 150,
        frequency: 'MONTHLY',
        firstDueDate: '2026-07-01',
        notes: undefined,
      },
    });
  });
});
