import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { LoginPage } from './LoginPage';

const loginMock = vi.fn();

vi.mock('../../lib/auth', () => ({
  useAuth: () => ({
    user: null,
    initializing: false,
    login: loginMock,
    logout: vi.fn(),
    hasRole: () => false,
  }),
}));

function renderPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    loginMock.mockReset();
  });

  it('renders email and password fields', () => {
    renderPage();
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
  });

  it('shows validation errors when fields are empty', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('rejects an invalid email format', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email');
    await userEvent.type(screen.getByLabelText('Password'), 'some-password-1');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('submits valid credentials', async () => {
    loginMock.mockResolvedValueOnce(undefined);
    renderPage();
    await userEvent.type(screen.getByLabelText('Email'), 'admin@clinic.test');
    await userEvent.type(screen.getByLabelText('Password'), 'super-secret-pass-1');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(loginMock).toHaveBeenCalledWith('admin@clinic.test', 'super-secret-pass-1'),
    );
  });

  it('shows a server error when login fails', async () => {
    loginMock.mockRejectedValueOnce(new Error('boom'));
    renderPage();
    await userEvent.type(screen.getByLabelText('Email'), 'admin@clinic.test');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password-1');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(
      await screen.findByText('Unable to sign in. Try again.'),
    ).toBeInTheDocument();
  });
});
