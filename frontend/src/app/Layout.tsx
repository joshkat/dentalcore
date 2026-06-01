import {
  BarChart3,
  BellRing,
  Calendar,
  ClipboardList,
  FileSignature,
  FileText,
  LayoutDashboard,
  ListTodo,
  Settings,
  Shield,
  ShieldCheck,
  Stethoscope,
  UserCog,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { useState, type DragEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { LanguageSync } from '../features/settings/LanguageSync';
import { useAuth } from '../lib/auth';
import type { Role } from '../types/api';
import { PaneManager } from './panes/PaneManager';
import { PANE_DRAG_TYPE, PaneProvider, usePanes } from './panes/PaneProvider';

interface NavItem {
  to: string;
  /** Key in the `nav` namespace. */
  labelKey: string;
  icon: LucideIcon;
  roles?: Role[];
}

const navItems: NavItem[] = [
  { to: '/', labelKey: 'dashboard', icon: LayoutDashboard },
  { to: '/schedule', labelKey: 'schedule', icon: Calendar },
  { to: '/patients', labelKey: 'patients', icon: Users },
  { to: '/recall', labelKey: 'recall', icon: BellRing },
  { to: '/providers', labelKey: 'providers', icon: Stethoscope },
  { to: '/procedures', labelKey: 'procedures', icon: ClipboardList },
  { to: '/insurance', labelKey: 'insurance', icon: Shield },
  { to: '/claims', labelKey: 'claims', icon: FileText },
  {
    to: '/forms',
    labelKey: 'forms',
    icon: FileSignature,
    roles: ['ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK'],
  },
  {
    to: '/worklists',
    labelKey: 'worklists',
    icon: ListTodo,
    roles: ['ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK', 'BILLING'],
  },
  {
    to: '/reports',
    labelKey: 'reports',
    icon: BarChart3,
    roles: ['ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK', 'BILLING'],
  },
  { to: '/users', labelKey: 'users', icon: UserCog, roles: ['ADMIN'] },
  { to: '/admin', labelKey: 'admin', icon: ShieldCheck, roles: ['ADMIN'] },
  { to: '/settings', labelKey: 'settings', icon: Settings },
];

export function Layout() {
  return (
    <PaneProvider>
      <LanguageSync />
      <LayoutShell />
    </PaneProvider>
  );
}

function LayoutShell() {
  const { t } = useTranslation('nav');
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
    e.dataTransfer.setData('text/plain', t(item.labelKey));
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
            aria-label={collapsed ? t('expandSidebar') : t('collapseSidebar')}
            title={collapsed ? t('expandSidebar') : t('collapseSidebar')}
            className="rounded p-1 text-gray-500 hover:bg-gray-100 hover:text-gray-900"
          >
            {collapsed ? '»' : '«'}
          </button>
        </div>
        <nav
          className={`flex-1 space-y-1 ${collapsed ? 'p-2' : 'p-3'}`}
          aria-label={t('mainNavigation')}
        >
          {navItems
            .filter((item) => !item.roles || hasRole(...item.roles))
            .map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                draggable
                aria-label={t(item.labelKey)}
                onDragStart={(e) => onNavDragStart(e, item)}
                onDragEnd={() => setDragging(false)}
                title={collapsed ? undefined : t('dragHint')}
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
                {!collapsed && t(item.labelKey)}
                {collapsed && (
                  <span className="pointer-events-none absolute left-full z-50 ml-2 hidden whitespace-nowrap rounded bg-gray-900 px-2 py-1 text-xs font-medium text-white shadow group-hover:block">
                    {t(item.labelKey)}
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
            {t('signOut')}
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
