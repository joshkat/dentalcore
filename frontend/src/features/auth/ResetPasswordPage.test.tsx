import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ResetPasswordPage } from './ResetPasswordPage';

const apiMock = vi.fn();

vi.mock('../../lib/api', () => ({
  api: (...args: unknown[]) => apiMock(...args),
  ApiError: class ApiError extends Error {},
}));

function renderWithToken(token?: string) {
  return render(
    <MemoryRouter initialEntries={[token ? `/reset-password?token=${token}` : '/reset-password']}>
      <ResetPasswordPage />
    </MemoryRouter>,
  );
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    apiMock.mockReset();
  });

  it('shows an error for a missing token', () => {
    renderWithToken();
    expect(screen.getByText(/this reset link is invalid/i)).toBeInTheDocument();
  });

  it('enforces password strength rules', async () => {
    renderWithToken('abc123');
    await userEvent.type(screen.getByLabelText('New password'), 'short');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'short');
    await userEvent.click(screen.getByRole('button', { name: /set new password/i }));

    expect(
      await screen.findByText('Password must be at least 12 characters'),
    ).toBeInTheDocument();
    expect(apiMock).not.toHaveBeenCalled();
  });

  it('requires passwords to match', async () => {
    renderWithToken('abc123');
    await userEvent.type(screen.getByLabelText('New password'), 'valid-password-12');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'different-pass-12');
    await userEvent.click(screen.getByRole('button', { name: /set new password/i }));

    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();
    expect(apiMock).not.toHaveBeenCalled();
  });

  it('submits a valid new password with the token', async () => {
    apiMock.mockResolvedValueOnce(undefined);
    renderWithToken('abc123');
    await userEvent.type(screen.getByLabelText('New password'), 'valid-password-12');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'valid-password-12');
    await userEvent.click(screen.getByRole('button', { name: /set new password/i }));

    await waitFor(() =>
      expect(apiMock).toHaveBeenCalledWith('/api/v1/auth/reset-password', {
        method: 'POST',
        body: { token: 'abc123', newPassword: 'valid-password-12' },
      }),
    );
  });
});
