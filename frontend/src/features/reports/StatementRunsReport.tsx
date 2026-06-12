import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { formatDate, formatDateTime, formatMoney } from '../../i18n/format';
import { downloadDocumentById } from '../documents/api';
import { ApiError } from '../../lib/api';
import type { StatementRun } from '../../types/api';
import { useCreateStatementRun, useStatementRun, useStatementRuns } from './api';

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
  const { t } = useTranslation('reports');
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
      setFormError(t('stmt.datesRequired'));
      return;
    }
    if (fromDate > toDate) {
      setFormError(t('stmt.fromAfterTo'));
      return;
    }
    if (!Number.isFinite(balance) || balance < 0) {
      setFormError(t('stmt.minBalanceNegative'));
      return;
    }
    if (
      !window.confirm(
        t('stmt.confirmGenerate', {
          balance: formatMoney(balance),
          from: formatDate(fromDate),
          to: formatDate(toDate),
        }),
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
          setFormError(e instanceof ApiError ? e.message : t('stmt.generateFailed')),
      },
    );
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-gray-900">{t('stmt.title')}</h2>

      <div className="flex flex-wrap items-end gap-3 rounded-md bg-gray-50 p-4">
        <div>
          <label htmlFor="stmt-from" className="block text-sm font-medium text-gray-700">
            {t('stmt.from')}
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
            {t('stmt.to')}
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
            {t('stmt.minBalance')}
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
          {t('stmt.generate')}
        </Button>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {formError}
        </p>
      )}

      {created && (
        <p className="rounded-md bg-green-50 p-3 text-sm text-green-800" data-testid="stmt-run-created">
          {t('stmt.createdSummary', {
            count: created.totalAccounts,
            amount: formatMoney(created.totalAmount),
          })}
        </p>
      )}

      {isPending ? (
        <Spinner label={t('stmt.loadingRuns')} />
      ) : !runs || runs.length === 0 ? (
        <p className="text-sm text-gray-500">{t('stmt.noRuns')}</p>
      ) : (
        <table className="min-w-full divide-y divide-gray-200 text-sm" data-testid="stmt-run-history">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-2 pr-3">{t('stmt.col.created')}</th>
              <th className="py-2 pr-3">{t('stmt.col.range')}</th>
              <th className="py-2 pr-3 text-right">{t('stmt.col.minBalance')}</th>
              <th className="py-2 pr-3 text-right">{t('stmt.col.accounts')}</th>
              <th className="py-2 pr-3 text-right">{t('stmt.col.total')}</th>
              <th className="py-2 pr-3">{t('stmt.col.status')}</th>
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
  const { t } = useTranslation('reports');
  return (
    <>
      <tr>
        <td className="py-2 pr-3 whitespace-nowrap text-gray-600">
          {formatDateTime(run.createdAt)}
        </td>
        <td className="py-2 pr-3 whitespace-nowrap text-gray-600">
          {formatDate(run.fromDate)} → {formatDate(run.toDate)}
        </td>
        <td className="py-2 pr-3 text-right">{formatMoney(run.minBalance)}</td>
        <td className="py-2 pr-3 text-right">{run.totalAccounts}</td>
        <td className="py-2 pr-3 text-right font-medium">{formatMoney(run.totalAmount)}</td>
        <td className="py-2 pr-3">
          <Badge tone={statusTone(run.status)}>{run.status}</Badge>
        </td>
        <td className="py-2 text-right">
          <Button variant="ghost" onClick={onToggle}>
            {expanded ? t('stmt.hideItems') : t('stmt.viewItems')}
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
  const { t } = useTranslation('reports');
  const { data, isPending } = useStatementRun(runId);
  const [error, setError] = useState<string | null>(null);

  if (isPending) return <Spinner label={t('stmt.loadingItems')} />;
  const items = data?.items ?? [];
  if (items.length === 0)
    return <p className="text-sm text-gray-500">{t('stmt.noAccounts')}</p>;

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
            <th className="py-1 pr-3">{t('stmt.col.guarantor')}</th>
            <th className="py-1 pr-3 text-right">{t('stmt.col.balance')}</th>
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
              <td className="py-1.5 pr-3 text-right">{formatMoney(item.balance)}</td>
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
                      setError(t('stmt.downloadFailed'));
                    }
                  }}
                >
                  {t('stmt.pdf')}
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
