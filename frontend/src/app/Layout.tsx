import {
  BarChart3,
  BellRing,
  Calendar,
  ClipboardList,
  FileSignature,
  FileText,
  LayoutDashboard,
  ListTodo,
  Shield,
  Stethoscope,
  UserCog,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { useState, type DragEvent } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import type { Role } from '../types/api';
import { PaneManager } from './panes/PaneManager';
import { PANE_DRAG_TYPE, PaneProvider, usePanes } from './panes/PaneProvider';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  roles?: Role[];
}

const navItems: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/schedule', label: 'Schedule', icon: Calendar },
  { to: '/patients', label: 'Patients', icon: Users },
  { to: '/recall', label: 'Recall', icon: BellRing },
  { to: '/providers', label: 'Providers', icon: Stethoscope },
  { to: '/procedures', label: 'Procedures', icon: ClipboardList },
  { to: '/insurance', label: 'Insurance', icon: Shield },
  { to: '/claims', label: 'Claims', icon: FileText },
  {
    to: '/forms',
    label: 'Forms',
    icon: FileSignature,
    roles: ['ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK'],
  },
  {
    to: '/worklists',
    label: 'Worklists',
    icon: ListTodo,
    roles: ['ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK', 'BILLING'],
  },
  {
    to: '/reports',
    label: 'Reports',
    icon: BarChart3,
    roles: ['ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK', 'BILLING'],
  },
  { to: '/users', label: 'Users', icon: UserCog, roles: ['ADMIN'] },
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
  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem('dentalcore.sidebar') === 'collapsed',
  );

  const toggleSidebar = () => {
    setCollapsed((c) => {
      localStorage.setItem('dentalcore.sidebar', c ? 'open' : 'collapsed');
      return !c;
    });
  };

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
    <div className="flex h-screen w-screen max-w-full overflow-hidden">
      <aside
        className={`flex shrink-0 flex-col border-r border-gray-200 bg-white transition-all ${
          collapsed ? 'w-12' : 'w-56'
        }`}
      >
        <div
          className={`flex h-14 items-center border-b border-gray-200 ${
            collapsed ? 'justify-center' : 'justify-between px-4'
          }`}
        >
          {!collapsed && <span className="text-lg font-bold text-brand-700">DentalCore</span>}
          <button
            onClick={toggleSidebar}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className="rounded p-1 text-gray-500 hover:bg-gray-100 hover:text-gray-900"
          >
            {collapsed ? '»' : '«'}
          </button>
        </div>
        <nav
          className={`flex-1 space-y-1 ${collapsed ? 'p-2' : 'p-3'}`}
          aria-label="Main navigation"
        >
          {navItems
            .filter((item) => !item.roles || hasRole(...item.roles))
            .map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                draggable
                aria-label={item.label}
                onDragStart={(e) => onNavDragStart(e, item)}
                onDragEnd={() => setDragging(false)}
                title={collapsed ? undefined : 'Drag into the workspace to open in a split pane'}
                className={({ isActive }) =>
                  `group relative flex cursor-grab items-center rounded-md text-sm font-medium active:cursor-grabbing ${
                    collapsed ? 'justify-center py-2' : 'gap-3 px-3 py-2'
                  } ${
                    isActive
                      ? 'bg-brand-50 text-brand-700'
                      : 'text-gray-700 hover:bg-gray-50'
                  }`
                }
              >
                <item.icon size={18} className="shrink-0" aria-hidden />
                {!collapsed && item.label}
                {collapsed && (
                  <span className="pointer-events-none absolute left-full z-50 ml-2 hidden whitespace-nowrap rounded bg-gray-900 px-2 py-1 text-xs font-medium text-white shadow group-hover:block">
                    {item.label}
                  </span>
                )}
              </NavLink>
            ))}
        </nav>
        {!collapsed && (
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
        )}
      </aside>
      <main className="flex min-h-0 min-w-0 flex-1 overflow-hidden">
        <PaneManager>
          <Outlet />
        </PaneManager>
      </main>
    </div>
  );
}
