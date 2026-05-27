import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { DuplicatePair, MergeResult } from '../../types/api';
import { MergePatientsModal } from './MergePatientsModal';

const mutate = vi.fn();

vi.mock('./api', () => ({
  useMergePatients: () => ({ mutate, isPending: false }),
}));

const pair: DuplicatePair = {
  first: { patientId: 'id-a', name: 'Alice Smith', dateOfBirth: '1990-01-01', status: 'ACTIVE' },
  second: { patientId: 'id-b', name: 'Alyce Smith', dateOfBirth: '1990-01-01', status: 'ACTIVE' },
  score: 0.92,
  reasons: ['Same DOB', 'Similar name'],
};

const onClose = vi.fn();

function renderModal() {
  return render(<MergePatientsModal pair={pair} onClose={onClose} />);
}

describe('MergePatientsModal', () => {
  beforeEach(() => {
    mutate.mockReset();
    onClose.mockReset();
  });

  it('keeps the confirm button disabled until MERGE is typed exactly', async () => {
    renderModal();
    const confirm = screen.getByRole('button', { name: 'Merge patients' });
    expect(confirm).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Type MERGE to confirm'), 'merge');
    expect(confirm).toBeDisabled();

    await userEvent.clear(screen.getByLabelText('Type MERGE to confirm'));
    await userEvent.type(screen.getByLabelText('Type MERGE to confirm'), 'MERGE');
    expect(confirm).toBeEnabled();
  });

  it('merges into the kept record (default: keep first)', async () => {
    renderModal();
    expect(
      screen.getByText(/All records from/),
    ).toHaveTextContent('All records from Alyce Smith will move to Alice Smith; Alyce Smith will be archived.');

    await userEvent.type(screen.getByLabelText('Type MERGE to confirm'), 'MERGE');
    await userEvent.click(screen.getByRole('button', { name: 'Merge patients' }));

    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate.mock.calls[0][0]).toEqual({ targetId: 'id-a', sourceId: 'id-b' });
  });

  it('swapping the direction swaps target and source', async () => {
    renderModal();
    await userEvent.click(screen.getByLabelText(/Keep Alyce Smith/));
    expect(screen.getByText(/All records from/)).toHaveTextContent(
      'All records from Alice Smith will move to Alyce Smith',
    );

    await userEvent.type(screen.getByLabelText('Type MERGE to confirm'), 'MERGE');
    await userEvent.click(screen.getByRole('button', { name: 'Merge patients' }));

    expect(mutate.mock.calls[0][0]).toEqual({ targetId: 'id-b', sourceId: 'id-a' });
  });

  it('shows repointed and skipped counts after a successful merge', async () => {
    const result: MergeResult = {
      targetId: 'id-a',
      sourceId: 'id-b',
      repointed: { appointments: 3, ledger_entries: 7 },
      skipped: { coverages: 1 },
    };
    mutate.mockImplementation((_vars, opts) => opts?.onSuccess?.(result));

    renderModal();
    await userEvent.type(screen.getByLabelText('Type MERGE to confirm'), 'MERGE');
    await userEvent.click(screen.getByRole('button', { name: 'Merge patients' }));

    expect(screen.getByText(/Merge complete/)).toBeInTheDocument();
    expect(screen.getByText(/appointments: 3, ledger_entries: 7/)).toBeInTheDocument();
    expect(screen.getByText(/coverages: 1/)).toBeInTheDocument();
  });
});
