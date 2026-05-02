import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Spinner } from '../../components/Spinner';
import { useAuth } from '../../lib/auth';
import type { AppointmentStatus, TreatmentPlanStatus } from '../../types/api';
import { STATUS_LABELS } from '../appointments/api';
import { useAsapList, useUnscheduledTreatment } from '../reports/api';

const TABS = ['Unscheduled treatment', 'ASAP list'] as const;
type Tab = (typeof TABS)[number];

const money = (n: number) => `$${n.toFixed(2)}`;

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
  const { hasRole } = useAuth();
  const canSee = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK', 'BILLING');
  const [tab, setTab] = useState<Tab>('Unscheduled treatment');

  if (!canSee) {
    return (
      <div className="p-8 text-sm text-gray-600">
        You do not have permission to view worklists.
      </div>
    );
  }

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900">Worklists</h1>

      <nav className="mt-6 flex gap-1 border-b border-gray-200" aria-label="Worklists">
        {TABS.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium ${
              tab === t
                ? 'border-b-2 border-brand-600 text-brand-700'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {t}
          </button>
        ))}
      </nav>

      <div className="mt-6 rounded-lg bg-white p-6 shadow">
        {tab === 'Unscheduled treatment' && <UnscheduledTreatmentList />}
        {tab === 'ASAP list' && <AsapList />}
      </div>
    </div>
  );
}

function UnscheduledTreatmentList() {
  const { data, isPending } = useUnscheduledTreatment();
  if (isPending) return <Spinner label="Loading worklist…" />;
  if (!data || data.length === 0) {
    return <p className="text-sm text-gray-500">No unscheduled treatment.</p>;
  }

  const rows = [...data].sort((a, b) => b.remainingValue - a.remainingValue);

  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">Patient</th>
          <th className="py-2 pr-3">Phone</th>
          <th className="py-2 pr-3">Plan</th>
          <th className="py-2 pr-3">Status</th>
          <th className="py-2 pr-3 text-right">Planned</th>
          <th className="py-2 pr-3 text-right">Remaining value</th>
          <th className="py-2">Recall</th>
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
              <Badge tone={planTone[row.planStatus] ?? 'gray'}>{row.planStatus}</Badge>
            </td>
            <td className="py-2 pr-3 text-right">{row.plannedCount}</td>
            <td className="py-2 pr-3 text-right font-medium">{money(row.remainingValue)}</td>
            <td className="py-2 text-gray-600">{row.nextRecallDate ?? '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function AsapList() {
  const { data, isPending } = useAsapList();
  if (isPending) return <Spinner label="Loading worklist…" />;
  if (!data || data.length === 0) {
    return <p className="text-sm text-gray-500">No ASAP requests.</p>;
  }

  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">Patient</th>
          <th className="py-2 pr-3">Phone</th>
          <th className="py-2 pr-3">Provider</th>
          <th className="py-2 pr-3">Scheduled for</th>
          <th className="py-2">Status</th>
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
              {new Date(row.startsAt).toLocaleString(undefined, {
                weekday: 'short',
                month: 'short',
                day: 'numeric',
                hour: 'numeric',
                minute: '2-digit',
              })}
            </td>
            <td className="py-2">
              <Badge tone={appointmentTone[row.status] ?? 'gray'}>
                {STATUS_LABELS[row.status] ?? row.status}
              </Badge>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
