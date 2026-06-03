import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Spinner } from '../../components/Spinner';
import { formatDate, formatMoney } from '../../i18n/format';
import { useAuth } from '../../lib/auth';
import type { AppointmentStatus, TreatmentPlanStatus } from '../../types/api';
import { useAsapList, useUnscheduledTreatment } from '../reports/api';

const TABS = ['unscheduled', 'asap'] as const;
type Tab = (typeof TABS)[number];

const planTone: Record<TreatmentPlanStatus, 'blue' | 'green' | 'yellow' | 'gray' | 'red'> = {
  DRAFT: 'gray',
  PRESENTED: 'blue',
  APPROVED: 'green',
  IN_PROGRESS: 'yellow',
  COMPLETED: 'green',
  CANCELLED: 'red',
};

const appointmentTone: Record<AppointmentStatus, 'blue' | 'green' | 'yellow' | 'gray' | 'red'> = {
  SCHEDULED: 'blue',
  CONFIRMED: 'green',
  CHECKED_IN: 'yellow',
  IN_PROGRESS: 'yellow',
  COMPLETED: 'green',
  NO_SHOW: 'red',
  CANCELLED: 'gray',
};

export function WorklistsPage() {
  const { t } = useTranslation('worklists');
  const { hasRole } = useAuth();
  const canSee = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK', 'BILLING');
  const [tab, setTab] = useState<Tab>('unscheduled');

  if (!canSee) {
    return <div className="p-8 text-sm text-gray-600">{t('noPermission')}</div>;
  }

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900">{t('title')}</h1>

      <nav className="mt-6 flex gap-1 border-b border-gray-200" aria-label={t('title')}>
        {TABS.map((tabId) => (
          <button
            key={tabId}
            onClick={() => setTab(tabId)}
            className={`px-4 py-2 text-sm font-medium ${
              tab === tabId
                ? 'border-b-2 border-brand-600 text-brand-700'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {t(`tabs.${tabId}`)}
          </button>
        ))}
      </nav>

      <div className="mt-6 rounded-lg bg-white p-6 shadow">
        {tab === 'unscheduled' && <UnscheduledTreatmentList />}
        {tab === 'asap' && <AsapList />}
      </div>
    </div>
  );
}

function UnscheduledTreatmentList() {
  const { t } = useTranslation('worklists');
  const { data, isPending } = useUnscheduledTreatment();
  if (isPending) return <Spinner label={t('loadingWorklist')} />;
  if (!data || data.length === 0) {
    return <p className="text-sm text-gray-500">{t('noUnscheduledTreatment')}</p>;
  }

  const rows = [...data].sort((a, b) => b.remainingValue - a.remainingValue);

  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">{t('patient')}</th>
          <th className="py-2 pr-3">{t('phone')}</th>
          <th className="py-2 pr-3">{t('plan')}</th>
          <th className="py-2 pr-3">{t('status')}</th>
          <th className="py-2 pr-3 text-right">{t('planned')}</th>
          <th className="py-2 pr-3 text-right">{t('remainingValue')}</th>
          <th className="py-2">{t('recall')}</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {rows.map((row) => (
          <tr key={row.planId}>
            <td className="py-2 pr-3">
              <Link
                to={`/patients/${row.patientId}`}
                className="font-medium text-brand-600 hover:underline"
              >
                {row.patientName}
              </Link>
            </td>
            <td className="py-2 pr-3 text-gray-600">{row.phone ?? '—'}</td>
            <td className="py-2 pr-3 text-gray-900">{row.planTitle}</td>
            <td className="py-2 pr-3">
              <Badge tone={planTone[row.planStatus] ?? 'gray'}>
                {t(`planStatus.${row.planStatus}`, { defaultValue: row.planStatus })}
              </Badge>
            </td>
            <td className="py-2 pr-3 text-right">{row.plannedCount}</td>
            <td className="py-2 pr-3 text-right font-medium">
              {formatMoney(row.remainingValue)}
            </td>
            <td className="py-2 text-gray-600">{row.nextRecallDate ?? '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function AsapList() {
  const { t } = useTranslation('worklists');
  const { data, isPending } = useAsapList();
  if (isPending) return <Spinner label={t('loadingWorklist')} />;
  if (!data || data.length === 0) {
    return <p className="text-sm text-gray-500">{t('noAsapRequests')}</p>;
  }

  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">{t('patient')}</th>
          <th className="py-2 pr-3">{t('phone')}</th>
          <th className="py-2 pr-3">{t('provider')}</th>
          <th className="py-2 pr-3">{t('scheduledFor')}</th>
          <th className="py-2">{t('status')}</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {data.map((row) => (
          <tr key={row.appointmentId}>
            <td className="py-2 pr-3">
              <Link
                to={`/patients/${row.patientId}`}
                className="font-medium text-brand-600 hover:underline"
              >
                {row.patientName}
              </Link>
            </td>
            <td className="py-2 pr-3 text-gray-600">{row.phone ?? '—'}</td>
            <td className="py-2 pr-3 text-gray-900">{row.providerName}</td>
            <td className="py-2 pr-3 text-gray-600">
              {formatDate(new Date(row.startsAt), {
                weekday: 'short',
                month: 'short',
                day: 'numeric',
                hour: 'numeric',
                minute: '2-digit',
              })}
            </td>
            <td className="py-2">
              <Badge tone={appointmentTone[row.status] ?? 'gray'}>
                {t(`schedule:status.${row.status}`, { defaultValue: row.status })}
              </Badge>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
