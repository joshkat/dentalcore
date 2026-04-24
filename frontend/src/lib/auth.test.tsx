import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './auth';

const { apiMock, refreshSessionMock, setAccessTokenMock } = vi.hoisted(() => ({
  apiMock: vi.fn(),
  refreshSessionMock: vi.fn(),
  setAccessTokenMock: vi.fn(),
}));

vi.mock('./api', () => ({
  api: apiMock,
  refreshSession: refreshSessionMock,
  setAccessToken: setAccessTokenMock,
}));

function LogoutProbe() {
  const { logout, initializing } = useAuth();
  if (initializing) return <p>loading</p>;
  return (
    <button type="button" onClick={() => logout().catch(() => {})}>
      Log out
    </button>
  );
}

describe('AuthProvider logout hygiene', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    refreshSessionMock.mockResolvedValue(null);
    apiMock.mockResolvedValue(undefined);
    queryClient = new QueryClient();
  });

  async function renderAndLogout() {
    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <LogoutProbe />
        </AuthProvider>
      </QueryClientProvider>,
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Log out' }));
    await waitFor(() =>
      expect(apiMock).toHaveBeenCalledWith('/api/v1/auth/logout', { method: 'POST' }),
    );
  }

  it('clears the React Query cache and the persisted pane layout', async () => {
    queryClient.setQueryData(['patients', 'list'], [{ id: 'p1', lastName: 'Smith' }]);
    window.localStorage.setItem(
      'dentalcore.panes',
      JSON.stringify({ type: 'leaf', id: 'primary', kind: 'primary' }),
    );
    window.localStorage.setItem('dentalcore.sidebar', 'collapsed');

    await renderAndLogout();

    await waitFor(() => expect(queryClient.getQueryCache().getAll()).toHaveLength(0));
    expect(window.localStorage.getItem('dentalcore.panes')).toBeNull();
    // UI preference, not sensitive — must survive logout.
    expect(window.localStorage.getItem('dentalcore.sidebar')).toBe('collapsed');
    expect(setAccessTokenMock).toHaveBeenCalledWith(null);
  });

  it('still clears local state when the logout request fails', async () => {
    apiMock.mockRejectedValueOnce(new Error('network down'));
    queryClient.setQueryData(['claims'], []);
    window.localStorage.setItem('dentalcore.panes', '{}');

    await renderAndLogout();

    await waitFor(() => expect(queryClient.getQueryCache().getAll()).toHaveLength(0));
    expect(window.localStorage.getItem('dentalcore.panes')).toBeNull();
    expect(setAccessTokenMock).toHaveBeenCalledWith(null);
  });
});
