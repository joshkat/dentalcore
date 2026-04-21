import type { DragEvent } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import type { Role } from '../types/api';
import { PaneManager } from './panes/PaneManager';
import { PANE_DRAG_TYPE, PaneProvider, usePanes } from './panes/PaneProvider';

interface NavItem {
  to: string;
  label: string;
  roles?: Role[];
}

const navItems: NavItem[] = [
  { to: '/', label: 'Dashboard' },
  { to: '/schedule', label: 'Schedule' },
  { to: '/patients', label: 'Patients' },
  { to: '/recall', label: 'Recall' },
  { to: '/providers', label: 'Providers' },
  { to: '/procedures', label: 'Procedures' },
  { to: '/insurance', label: 'Insurance' },
  { to: '/claims', label: 'Claims' },
  { to: '/reports', label: 'Reports' },
  { to: '/users', label: 'Users', roles: ['ADMIN'] },
];

export function Layout() {
  return (
    <PaneProvider>
      <LayoutShell />
    </PaneProvider>
  );
}

function LayoutShell() {
  const { user, logout, hasRole } = useAuth();
  const { setDragging } = usePanes();
  const navigate = useNavigate();

  const onLogout = async () => {
    await logout();
    navigate('/login');
  };

  const onNavDragStart = (e: DragEvent, item: NavItem) => {
    e.dataTransfer.setData(PANE_DRAG_TYPE, item.to);
    e.dataTransfer.setData('text/plain', item.label);
    e.dataTransfer.effectAllowed = 'copy';
    setDragging(true);
  };

  return (
    <div className="flex h-screen">
      <aside className="flex w-56 flex-col border-r border-gray-200 bg-white">
        <div className="flex h-14 items-center border-b border-gray-200 px-4">
          <span className="text-lg font-bold text-brand-700">DentalCore</span>
        </div>
        <nav className="flex-1 space-y-1 p-3" aria-label="Main navigation">
          {navItems
            .filter((item) => !item.roles || hasRole(...item.roles))
            .map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                draggable
                onDragStart={(e) => onNavDragStart(e, item)}
                onDragEnd={() => setDragging(false)}
                title="Drag into the workspace to open in a split pane"
                className={({ isActive }) =>
                  `block cursor-grab rounded-md px-3 py-2 text-sm font-medium active:cursor-grabbing ${
                    isActive
                      ? 'bg-brand-50 text-brand-700'
                      : 'text-gray-700 hover:bg-gray-50'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
        </nav>
        <div className="border-t border-gray-200 p-3">
          <p className="truncate text-sm font-medium text-gray-900">
            {user?.firstName} {user?.lastName}
          </p>
          <p className="truncate text-xs text-gray-500">{user?.email}</p>
          <button
            onClick={onLogout}
            className="mt-2 text-sm text-brand-600 hover:underline"
          >
            Sign out
          </button>
        </div>
      </aside>
      <main className="flex min-h-0 flex-1">
        <PaneManager>
          <Outlet />
        </PaneManager>
      </main>
    </div>
  );
}
