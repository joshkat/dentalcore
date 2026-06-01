import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { downloadDocumentById } from '../documents/api';
import { ApiError } from '../../lib/api';
import type { StatementRun } from '../../types/api';
import { useCreateStatementRun, useStatementRun, useStatementRuns } from './api';

const money = (n: number) => `$${n.toFixed(2)}`;

function localIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}

/** Previous calendar month: [first day, last day]. */
export function lastMonthRange(today = new Date()): { from: string; to: string } {
  const from = new Date(today.getFullYear(), today.getMonth() - 1, 1);
  const to = new Date(today.getFullYear(), today.getMonth(), 0);
  return { from: localIso(from), to: localIso(to) };
}

function statusTone(status: string): 'green' | 'red' | 'yellow' | 'gray' {
  if (status === 'COMPLETED') return 'green';
  if (status === 'FAILED') return 'red';
  if (status === 'PENDING' || status === 'RUNNING') return 'yellow';
  return 'gray';
}

export function StatementRunsReport() {
  const defaults = lastMonthRange();
  const [fromDate, setFromDate] = useState(defaults.from);
  const [toDate, setToDate] = useState(defaults.to);
  const [minBalance, setMinBalance] = useState('0');
  const [formError, setFormError] = useState<string | null>(null);
  const [created, setCreated] = useState<StatementRun | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const { data: runs, isPending } = useStatementRuns();
  const createRun = useCreateStatementRun();

  const onGenerate = () => {
    setFormError(null);
    setCreated(null);
    const balance = Number(minBalance);
    if (!fromDate || !toDate) {
      setFormError('From and To dates are required.');
      return;
    }
    if (fromDate > toDate) {
      setFormError('From date must be on or before the To date.');
      return;
    }
    if (!Number.isFinite(balance) || balance < 0) {
      setFormError('Minimum balance must be zero or more.');
      return;
    }
    if (
      !window.confirm(
        `Generate statements for all accounts with balance ≥ ${money(balance)} for activity ${fromDate} to ${toDate}?`,
      )
    ) {
      return;
    }
    createRun.mutate(
      { fromDate, toDate, minBalance: balance },
      {
        onSuccess: (run) => {
          setCreated(run);
          setExpandedId(run.id);
        },
        onError: (e) =>
          setFormError(e instanceof ApiError ? e.message : 'Failed to generate statements'),
      },
    );
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-gray-900">Statement runs</h2>

      <div className="flex flex-wrap items-end gap-3 rounded-md bg-gray-50 p-4">
        <div>
          <label htmlFor="stmt-from" className="block text-sm font-medium text-gray-700">
            From
          </label>
          <input
            id="stmt-from"
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          />
        </div>
        <div>
          <label htmlFor="stmt-to" className="block text-sm font-medium text-gray-700">
            To
          </label>
          <input
            id="stmt-to"
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          />
        </div>
        <div>
          <label htmlFor="stmt-min-balance" className="block text-sm font-medium text-gray-700">
            Min balance
          </label>
          <input
            id="stmt-min-balance"
            type="number"
            min="0"
            step="0.01"
            value={minBalance}
            onChange={(e) => setMinBalance(e.target.value)}
            className="mt-1 w-32 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          />
        </div>
        <Button onClick={onGenerate} loading={createRun.isPending}>
          Generate statements
        </Button>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {formError}
        </p>
      )}

      {created && (
        <p className="rounded-md bg-green-50 p-3 text-sm text-green-800" data-testid="stmt-run-created">
          Generated {created.totalAccounts} statement{created.totalAccounts === 1 ? '' : 's'}{' '}
          totalling {money(created.totalAmount)}.
        </p>
      )}

      {isPending ? (
        <Spinner label="Loading statement runs…" />
      ) : !runs || runs.length === 0 ? (
        <p className="text-sm text-gray-500">No statement runs yet.</p>
      ) : (
        <table className="min-w-full divide-y divide-gray-200 text-sm" data-testid="stmt-run-history">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-2 pr-3">Created</th>
              <th className="py-2 pr-3">Range</th>
              <th className="py-2 pr-3 text-right">Min balance</th>
              <th className="py-2 pr-3 text-right">Accounts</th>
              <th className="py-2 pr-3 text-right">Total</th>
              <th className="py-2 pr-3">Status</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {runs.map((run) => (
              <StatementRunRow
                key={run.id}
                run={run}
                expanded={expandedId === run.id}
                onToggle={() => setExpandedId((id) => (id === run.id ? null : run.id))}
              />
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function StatementRunRow({
  run,
  expanded,
  onToggle,
}: {
  run: StatementRun;
  expanded: boolean;
  onToggle: () => void;
}) {
  return (
    <>
      <tr>
        <td className="py-2 pr-3 whitespace-nowrap text-gray-600">
          {new Date(run.createdAt).toLocaleString()}
        </td>
        <td className="py-2 pr-3 whitespace-nowrap text-gray-600">
          {run.fromDate} → {run.toDate}
        </td>
        <td className="py-2 pr-3 text-right">{money(run.minBalance)}</td>
        <td className="py-2 pr-3 text-right">{run.totalAccounts}</td>
        <td className="py-2 pr-3 text-right font-medium">{money(run.totalAmount)}</td>
        <td className="py-2 pr-3">
          <Badge tone={statusTone(run.status)}>{run.status}</Badge>
        </td>
        <td className="py-2 text-right">
          <Button variant="ghost" onClick={onToggle}>
            {expanded ? 'Hide items' : 'View items'}
          </Button>
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={7} className="bg-gray-50 px-4 py-3">
            <StatementRunItems runId={run.id} />
          </td>
        </tr>
      )}
    </>
  );
}

function StatementRunItems({ runId }: { runId: string }) {
  const { data, isPending } = useStatementRun(runId);
  const [error, setError] = useState<string | null>(null);

  if (isPending) return <Spinner label="Loading statements…" />;
  const items = data?.items ?? [];
  if (items.length === 0)
    return <p className="text-sm text-gray-500">No accounts matched this run.</p>;

  return (
    <div className="space-y-2">
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      <table className="min-w-full text-sm">
        <thead>
          <tr className="text-left text-xs font-semibold uppercase text-gray-500">
            <th className="py-1 pr-3">Guarantor</th>
            <th className="py-1 pr-3 text-right">Balance</th>
            <th className="py-1" />
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {items.map((item) => (
            <tr key={item.documentId}>
              <td className="py-1.5 pr-3">
                <Link
                  to={`/patients/${item.guarantorPatientId}`}
                  className="font-medium text-brand-700 hover:underline"
                >
                  {item.guarantorName}
                </Link>
              </td>
              <td className="py-1.5 pr-3 text-right">{money(item.balance)}</td>
              <td className="py-1.5 text-right">
                <Button
                  variant="secondary"
                  onClick={async () => {
                    setError(null);
                    try {
                      await downloadDocumentById(
                        item.documentId,
                        `statement-${item.guarantorName.replace(/\s+/g, '-')}-${data?.fromDate ?? ''}.pdf`,
                      );
                    } catch {
                      setError('Download failed');
                    }
                  }}
                >
                  PDF
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
