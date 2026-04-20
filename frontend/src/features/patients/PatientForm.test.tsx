import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { PatientForm } from './PatientForm';
import { emptyPatient } from './schemas';

vi.mock('../providers/api', () => ({
  useProviders: () => ({ data: undefined }),
}));

const onSubmit = vi.fn();
const onCancel = vi.fn();

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <PatientForm
        defaultValues={emptyPatient}
        submitLabel="Register patient"
        onSubmit={onSubmit}
        onCancel={onCancel}
      />
    </QueryClientProvider>,
  );
}

describe('PatientForm', () => {
  beforeEach(() => {
    onSubmit.mockReset();
    onSubmit.mockResolvedValue(undefined);
  });

  it('requires name and date of birth', async () => {
    renderForm();
    await userEvent.click(screen.getByRole('button', { name: /register patient/i }));

    expect(await screen.findByText('First name is required')).toBeInTheDocument();
    expect(screen.getByText('Last name is required')).toBeInTheDocument();
    expect(screen.getByText('Date of birth is required')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('rejects a future date of birth', async () => {
    renderForm();
    await userEvent.type(screen.getByLabelText('First name'), 'Future');
    await userEvent.type(screen.getByLabelText('Last name'), 'Person');
    await userEvent.type(screen.getByLabelText('Date of birth'), '2999-01-01');
    await userEvent.type(screen.getByLabelText('Number'), '555-123-4567');
    await userEvent.click(screen.getByRole('button', { name: /register patient/i }));

    expect(
      await screen.findByText('Date of birth must be in the past'),
    ).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('rejects an invalid phone number', async () => {
    renderForm();
    await userEvent.type(screen.getByLabelText('First name'), 'Phone');
    await userEvent.type(screen.getByLabelText('Last name'), 'Check');
    await userEvent.type(screen.getByLabelText('Date of birth'), '1990-06-15');
    await userEvent.type(screen.getByLabelText('Number'), 'not-a-phone');
    await userEvent.click(screen.getByRole('button', { name: /register patient/i }));

    expect(await screen.findByText('Invalid phone number')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits a valid patient', async () => {
    renderForm();
    await userEvent.type(screen.getByLabelText('First name'), 'Valid');
    await userEvent.type(screen.getByLabelText('Last name'), 'Patient');
    await userEvent.type(screen.getByLabelText('Date of birth'), '1990-06-15');
    await userEvent.type(screen.getByLabelText('Number'), '555-123-4567');
    await userEvent.click(screen.getByRole('button', { name: /register patient/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toMatchObject({
      firstName: 'Valid',
      lastName: 'Patient',
      dateOfBirth: '1990-06-15',
      phones: [{ type: 'MOBILE', number: '555-123-4567', primary: true }],
    });
  });
});
