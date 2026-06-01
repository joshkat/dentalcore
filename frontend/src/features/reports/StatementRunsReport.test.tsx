import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { lastMonthRange, StatementRunsReport } from './StatementRunsReport';

const mutate = vi.fn();

vi.mock('./api', () => ({
  useStatementRuns: () => ({ data: [], isPending: false }),
  useStatementRun: () => ({ data: undefined, isPending: true }),
  useCreateStatementRun: () => ({ mutate, isPending: false }),
}));

function renderReport() {
  return render(
    <MemoryRouter>
      <StatementRunsReport />
    </MemoryRouter>,
  );
}

describe('StatementRunsReport', () => {
  beforeEach(() => {
    mutate.mockReset();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('defaults the range to last month and min balance to 0', () => {
    renderReport();
    const { from, to } = lastMonthRange();
    expect(screen.getByLabelText('From')).toHaveValue(from);
    expect(screen.getByLabelText('To')).toHaveValue(to);
    expect(screen.getByLabelText('Min balance')).toHaveValue(0);
  });

  it('blocks generation when From is after To', async () => {
    renderReport();
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-05-10' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-04-01' } });
    await userEvent.click(screen.getByRole('button', { name: 'Generate statements' }));

    expect(
      screen.getByText('From date must be on or before the To date.'),
    ).toBeInTheDocument();
    expect(window.confirm).not.toHaveBeenCalled();
    expect(mutate).not.toHaveBeenCalled();
  });

  it('blocks a negative minimum balance', async () => {
    renderReport();
    fireEvent.change(screen.getByLabelText('Min balance'), { target: { value: '-5' } });
    await userEvent.click(screen.getByRole('button', { name: 'Generate statements' }));

    expect(screen.getByText('Minimum balance must be zero or more.')).toBeInTheDocument();
    expect(mutate).not.toHaveBeenCalled();
  });

  it('generates after confirmation with the parsed payload', async () => {
    renderReport();
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-04-01' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-04-30' } });
    fireEvent.change(screen.getByLabelText('Min balance'), { target: { value: '25.50' } });
    await userEvent.click(screen.getByRole('button', { name: 'Generate statements' }));

    expect(window.confirm).toHaveBeenCalledTimes(1);
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      fromDate: '2026-04-01',
      toDate: '2026-04-30',
      minBalance: 25.5,
    });
  });

  it('does not generate when the confirmation is dismissed', async () => {
    vi.mocked(window.confirm).mockReturnValue(false);
    renderReport();
    await userEvent.click(screen.getByRole('button', { name: 'Generate statements' }));
    expect(mutate).not.toHaveBeenCalled();
  });
});
