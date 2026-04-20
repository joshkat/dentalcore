import { Link } from 'react-router-dom';
import { useAuth } from '../../lib/auth';
import { useDashboard } from '../reports/api';

export function DashboardPage() {
  const { user, hasRole } = useAuth();
  const { data } = useDashboard();
  const canSeeFinancials = hasRole('ADMIN', 'BILLING');

  const tiles: Array<{ title: string; value: string; hint: string; to: string }> = [
    {
      title: 'Active patients',
      value: data ? String(data.activePatients) : '…',
      hint: 'patients with ACTIVE status',
      to: '/patients',
    },
    {
      title: "Today's appointments",
      value: data ? `${data.todaysCompletedAppointments}/${data.todaysAppointments}` : '…',
      hint: 'completed / scheduled today',
      to: '/schedule',
    },
    ...(canSeeFinancials
      ? [
          {
            title: "Today's production",
            value: data ? `$${data.todaysProduction.toFixed(2)}` : '…',
            hint: `collections $${data ? data.todaysCollections.toFixed(2) : '…'}`,
            to: '/reports',
          },
          {
            title: 'Open claims',
            value: data ? String(data.openClaims) : '…',
            hint: 'draft, submitted, accepted, or denied',
            to: '/claims',
          },
        ]
      : []),
  ];

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900">
        Welcome back, {user?.firstName}
      </h1>
      <p className="mt-1 text-sm text-gray-500">
        Today is{' '}
        {new Date().toLocaleDateString(undefined, {
          weekday: 'long',
          year: 'numeric',
          month: 'long',
          day: 'numeric',
        })}
      </p>
      <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {tiles.map((tile) => (
          <Link
            key={tile.title}
            to={tile.to}
            className="rounded-lg bg-white p-6 shadow transition-shadow hover:shadow-md"
          >
            <h2 className="text-sm font-medium text-gray-500">{tile.title}</h2>
            <p className="mt-2 text-3xl font-semibold text-gray-900">{tile.value}</p>
            <p className="mt-1 text-xs text-gray-400">{tile.hint}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
