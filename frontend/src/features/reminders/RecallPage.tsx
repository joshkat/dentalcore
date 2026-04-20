import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
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
        <h1 className="text-2xl font-bold text-gray-900">Recall worklist</h1>
        {canRun && (
          <Button
            loading={runReminders.isPending}
            onClick={async () => {
              setError(null);
              try {
                await runReminders.mutateAsync();
              } catch (e) {
                setError(e instanceof ApiError ? e.message : 'Run failed');
              }
            }}
          >
            Send reminders now
          </Button>
        )}
      </div>
      <p className="mt-1 text-sm text-gray-500">
        Patients due or overdue for their recall visit. Reminders also run automatically each
        morning; consent and a 30-day cooldown are respected.
      </p>

      {summary && (
        <div className="mt-3 rounded-md bg-green-50 p-3 text-sm text-green-800" role="status">
          Run complete — appointments: {summary.appointmentSent} sent /{' '}
          {summary.appointmentSkipped} skipped · recall: {summary.recallSent} sent /{' '}
          {summary.recallSkipped} skipped · {summary.failed} failed
        </div>
      )}
      {error && (
        <p role="alert" className="mt-3 rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className="mt-4">
        <label htmlFor="recall-window" className="mr-2 text-sm text-gray-700">
          Show patients due within
        </label>
        <select
          id="recall-window"
          value={daysAhead}
          onChange={(e) => setDaysAhead(Number(e.target.value))}
          className="rounded-md border-0 px-3 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
        >
          <option value={0}>overdue only</option>
          <option value={14}>14 days</option>
          <option value={30}>30 days</option>
          <option value={90}>90 days</option>
        </select>
      </div>

      <div className="mt-4 overflow-hidden rounded-lg bg-white shadow">
        {isPending ? (
          <Spinner label="Loading worklist…" />
        ) : rows && rows.length === 0 ? (
          <p className="p-8 text-center text-sm text-gray-500">Nobody is due. 🎉</p>
        ) : (
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                {['Patient', 'Recall due', 'Phone', 'Email', 'Last reminder'].map((h) => (
                  <th
                    key={h}
                    className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500"
                  >
                    {h}
                  </th>
                ))}
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
                    {row.nextRecallDate <= today && <Badge tone="red">OVERDUE</Badge>}
                  </td>
                  <td className="px-4 py-3 text-gray-600">{row.phone ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-600">{row.email ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-600">
                    {row.lastReminderAt
                      ? new Date(row.lastReminderAt).toLocaleDateString()
                      : 'never'}
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
