import { useQueryClient } from '@tanstack/react-query';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { AuthResponse, AuthUser, Role } from '../types/api';
import { api, refreshSession, setAccessToken } from './api';

interface AuthContextValue {
  user: AuthUser | null;
  initializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  // AuthProvider sits inside QueryClientProvider (see main.tsx), so this works.
  const queryClient = useQueryClient();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    let cancelled = false;
    refreshSession().then((auth) => {
      if (!cancelled) {
        setUser(auth?.user ?? null);
        setInitializing(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await api<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: { email, password },
    });
    setAccessToken(auth.accessToken);
    setUser(auth.user);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api<void>('/api/v1/auth/logout', { method: 'POST' });
    } finally {
      setAccessToken(null);
      setUser(null);
      // Hygiene: drop all cached server data (may hold PHI) and the persisted
      // pane layout (pane paths can embed patient ids). 'dentalcore.sidebar'
      // is a pure UI preference and is intentionally kept.
      queryClient.clear();
      window.localStorage.removeItem('dentalcore.panes');
    }
  }, [queryClient]);

  const hasRole = useCallback(
    (...roles: Role[]) => user != null && roles.some((r) => user.roles.includes(r)),
    [user],
  );

  const value = useMemo(
    () => ({ user, initializing, login, logout, hasRole }),
    [user, initializing, login, logout, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
