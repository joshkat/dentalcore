import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { formatDate } from '../../i18n/format';
import { api, ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';

interface RecallRow {
  patientId: string;
  firstName: string;
  lastName: string;
  nextRecallDate: string;
  phone: string | null;
  email: string | null;
  lastReminderAt: string | null;
}

interface RunSummary {
  appointmentSent: number;
  appointmentSkipped: number;
  recallSent: number;
  recallSkipped: number;
  failed: number;
}

export function RecallPage() {
  const { t } = useTranslation('recall');
  const [daysAhead, setDaysAhead] = useState(14);
  const [summary, setSummary] = useState<RunSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const canRun = hasRole('ADMIN', 'FRONT_DESK');

  const { data: rows, isPending } = useQuery({
    queryKey: ['recall-worklist', daysAhead],
    queryFn: () => api<RecallRow[]>(`/api/v1/reminders/recall-worklist?daysAhead=${daysAhead}`),
  });

  const runReminders = useMutation({
    mutationFn: () => api<RunSummary>('/api/v1/reminders/run', { method: 'POST' }),
    onSuccess: (result) => {
      setSummary(result);
      queryClient.invalidateQueries({ queryKey: ['recall-worklist'] });
    },
  });

  const today = new Date().toISOString().slice(0, 10);

  return (
    <div className="p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-gray-900">{t('title')}</h1>
        {canRun && (
          <Button
            loading={runReminders.isPending}
            onClick={async () => {
              setError(null);
              try {
                await runReminders.mutateAsync();
              } catch (e) {
                setError(e instanceof ApiError ? e.message : t('runFailed'));
              }
            }}
          >
            {t('sendRemindersNow')}
          </Button>
        )}
      </div>
      <p className="mt-1 text-sm text-gray-500">{t('description')}</p>

      {summary && (
        <div className="mt-3 rounded-md bg-green-50 p-3 text-sm text-green-800" role="status">
          {t('runSummary', {
            appointmentSent: summary.appointmentSent,
            appointmentSkipped: summary.appointmentSkipped,
            recallSent: summary.recallSent,
            recallSkipped: summary.recallSkipped,
            failed: summary.failed,
          })}
        </div>
      )}
      {error && (
        <p role="alert" className="mt-3 rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className="mt-4">
        <label htmlFor="recall-window" className="mr-2 text-sm text-gray-700">
          {t('showPatientsDueWithin')}
        </label>
        <select
          id="recall-window"
          value={daysAhead}
          onChange={(e) => setDaysAhead(Number(e.target.value))}
          className="rounded-md border-0 px-3 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
        >
          <option value={0}>{t('overdueOnly')}</option>
          <option value={14}>{t('windowDays', { count: 14 })}</option>
          <option value={30}>{t('windowDays', { count: 30 })}</option>
          <option value={90}>{t('windowDays', { count: 90 })}</option>
        </select>
      </div>

      <div className="mt-4 overflow-hidden rounded-lg bg-white shadow">
        {isPending ? (
          <Spinner label={t('loadingWorklist')} />
        ) : rows && rows.length === 0 ? (
          <p className="p-8 text-center text-sm text-gray-500">{t('nobodyDue')}</p>
        ) : (
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                {(['patient', 'recallDue', 'phone', 'email', 'lastReminder'] as const).map(
                  (h) => (
                    <th
                      key={h}
                      className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500"
                    >
                      {t(h)}
                    </th>
                  ),
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows?.map((row) => (
                <tr key={row.patientId}>
                  <td className="px-4 py-3 font-medium">
                    <Link
                      to={`/patients/${row.patientId}`}
                      className="text-brand-700 hover:underline"
                    >
                      {row.lastName}, {row.firstName}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <span className="mr-2">{row.nextRecallDate}</span>
                    {row.nextRecallDate <= today && <Badge tone="red">{t('overdueBadge')}</Badge>}
                  </td>
                  <td className="px-4 py-3 text-gray-600">{row.phone ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-600">{row.email ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-600">
                    {row.lastReminderAt
                      ? formatDate(row.lastReminderAt, {
                          year: 'numeric',
                          month: 'numeric',
                          day: 'numeric',
                        })
                      : t('never')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
