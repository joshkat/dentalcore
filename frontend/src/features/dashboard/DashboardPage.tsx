import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { formatDate, formatMoney } from '../../i18n/format';
import { useAuth } from '../../lib/auth';
import { useDashboard } from '../reports/api';

export function DashboardPage() {
  const { t } = useTranslation('dashboard');
  const { user, hasRole } = useAuth();
  const { data } = useDashboard();
  const canSeeFinancials = hasRole('ADMIN', 'BILLING');

  const tiles: Array<{ title: string; value: string; hint: string; to: string }> = [
    {
      title: t('activePatients'),
      value: data ? String(data.activePatients) : '…',
      hint: t('activePatientsHint'),
      to: '/patients',
    },
    {
      title: t('todaysAppointments'),
      value: data ? `${data.todaysCompletedAppointments}/${data.todaysAppointments}` : '…',
      hint: t('todaysAppointmentsHint'),
      to: '/schedule',
    },
    ...(canSeeFinancials
      ? [
          {
            title: t('todaysProduction'),
            value: data ? formatMoney(data.todaysProduction) : '…',
            hint: t('collectionsHint', {
              amount: data ? formatMoney(data.todaysCollections) : '…',
            }),
            to: '/reports',
          },
          {
            title: t('openClaims'),
            value: data ? String(data.openClaims) : '…',
            hint: t('openClaimsHint'),
            to: '/claims',
          },
        ]
      : []),
  ];

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900">
        {t('welcomeBack', { name: user?.firstName ?? '' })}
      </h1>
      <p className="mt-1 text-sm text-gray-500">
        {t('todayIs', {
          date: formatDate(new Date(), {
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric',
          }),
        })}
      </p>
      <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {tiles.map((tile) => (
          <Link
            key={tile.to}
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
