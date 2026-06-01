import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import i18n from '../i18n';
import { Layout } from './Layout';

vi.mock('../lib/auth', () => ({
  useAuth: () => ({
    user: {
      id: 'u1',
      email: 'admin@clinic.test',
      firstName: 'Ada',
      lastName: 'Admin',
      roles: ['ADMIN'],
    },
    initializing: false,
    login: vi.fn(),
    logout: vi.fn(),
    hasRole: () => true,
  }),
}));

// LanguageSync fetches user preferences; keep the test offline.
vi.mock('../features/settings/api', () => ({
  usePreferences: () => ({ data: undefined }),
}));

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<div>home content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('Layout i18n', () => {
  it('renders English nav labels by default', () => {
    renderLayout();
    expect(screen.getByRole('link', { name: 'Patients' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Schedule' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Settings' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument();
  });

  it('switching the language flips nav text to Spanish', async () => {
    renderLayout();
    expect(screen.getByRole('link', { name: 'Patients' })).toBeInTheDocument();

    await act(async () => {
      await i18n.changeLanguage('es');
    });

    expect(screen.getByRole('link', { name: 'Pacientes' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Agenda' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Configuración' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Patients' })).not.toBeInTheDocument();
  });

  it('switching back restores English', async () => {
    renderLayout();
    await act(async () => {
      await i18n.changeLanguage('es');
    });
    expect(screen.getByRole('link', { name: 'Pacientes' })).toBeInTheDocument();

    await act(async () => {
      await i18n.changeLanguage('en');
    });
    expect(screen.getByRole('link', { name: 'Patients' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Pacientes' })).not.toBeInTheDocument();
  });
});
