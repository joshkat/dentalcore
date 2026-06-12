import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { formatMoney } from '../../i18n/format';
import { useAuth } from '../../lib/auth';
import {
  useAppointmentsByProvider,
  useArAging,
  useCollections,
  useDailyProduction,
  useDaySheet,
  usePatientGrowth,
  useProviderUtilization,
  type ArAgingRow,
  type DaySheetEntryType,
} from './api';
import { StatementRunsReport } from './StatementRunsReport';

const REPORTS = [
  'Appointments by provider',
  'Daily production',
  'Day sheet',
  'Patient growth',
  'Provider utilization',
  'A/R aging',
  'Collections',
  'Statement runs',
] as const;
type Report = (typeof REPORTS)[number];

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

export function ReportsPage() {
  const { t } = useTranslation('reports');
  const { hasRole } = useAuth();
  const canSeeFinancials = hasRole('ADMIN', 'BILLING');
  const canSeeDaySheet = hasRole('ADMIN', 'BILLING', 'FRONT_DESK');
  // READ_ONLY cannot fetch any report endpoint, so don't render the page at all
  const canSeeReports = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK', 'BILLING');
  const reports = REPORTS.filter((r) => {
    if (r === 'Daily production' || r === 'A/R aging' || r === 'Statement runs')
      return canSeeFinancials;
    if (r === 'Day sheet' || r === 'Collections') return canSeeDaySheet;
    return true;
  });

  const [report, setReport] = useState<Report>(reports[0]);
  const [from, setFrom] = useState(isoDaysAgo(30));
  const [to, setTo] = useState(isoDaysAgo(0));
  const [date, setDate] = useState(isoDaysAgo(0));

  const showRange =
    report !== 'Patient growth' &&
    report !== 'Day sheet' &&
    report !== 'A/R aging' &&
    report !== 'Collections' &&
    report !== 'Statement runs'; // has its own form row

  if (!canSeeReports) {
    return (
      <div className="p-8 text-sm text-gray-600">
        {t('noPermission')}
      </div>
    );
  }

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900">{t('title')}</h1>

      <div className="mt-4 flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor="report-select" className="block text-sm font-medium text-gray-700">
            {t('reportLabel')}
          </label>
          <select
            id="report-select"
            value={report}
            onChange={(e) => setReport(e.target.value as Report)}
            className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          >
            {reports.map((r) => (
              <option key={r} value={r}>
                {t(`reportName.${r}`)}
              </option>
            ))}
          </select>
        </div>
        {report === 'Day sheet' && (
          <div>
            <label htmlFor="report-date" className="block text-sm font-medium text-gray-700">
              {t('date')}
            </label>
            <input
              id="report-date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
          </div>
        )}
        {showRange && (
          <>
            <div>
              <label htmlFor="report-from" className="block text-sm font-medium text-gray-700">
                {t('from')}
              </label>
              <input
                id="report-from"
                type="date"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
                className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
            </div>
            <div>
              <label htmlFor="report-to" className="block text-sm font-medium text-gray-700">
                {t('to')}
              </label>
              <input
                id="report-to"
                type="date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
            </div>
          </>
        )}
      </div>

      <div className="mt-6 rounded-lg bg-white p-6 shadow">
        {report === 'Appointments by provider' && <AppointmentsReport from={from} to={to} />}
        {report === 'Daily production' && canSeeFinancials && (
          <ProductionReport from={from} to={to} />
        )}
        {report === 'Day sheet' && canSeeDaySheet && <DaySheetReportView date={date} />}
        {report === 'Patient growth' && <GrowthReport />}
        {report === 'Provider utilization' && <UtilizationReport from={from} to={to} />}
        {report === 'A/R aging' && canSeeFinancials && <ArAgingReportView />}
        {report === 'Collections' && canSeeDaySheet && <CollectionsReportView />}
        {report === 'Statement runs' && canSeeFinancials && <StatementRunsReport />}
      </div>
    </div>
  );
}

function Bar({ value, max, color = '#3b82f6' }: { value: number; max: number; color?: string }) {
  const width = max > 0 ? Math.max((value / max) * 100, value > 0 ? 2 : 0) : 0;
  return (
    <span className="flex h-2 w-32 overflow-hidden rounded bg-gray-100">
      <span style={{ width: `${width}%`, backgroundColor: color }} />
    </span>
  );
}

function AppointmentsReport({ from, to }: { from: string; to: string }) {
  const { t } = useTranslation('reports');
  const { data, isPending } = useAppointmentsByProvider(from, to);
  if (isPending) return <Spinner label={t('running')} />;
  if (!data || data.length === 0)
    return <p className="text-sm text-gray-500">{t('noAppointments')}</p>;
  const max = Math.max(...data.map((r) => r.total));
  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">{t('col.provider')}</th>
          <th className="py-2 pr-3">{t('col.total')}</th>
          <th className="py-2 pr-3" />
          <th className="py-2 pr-3">{t('col.completed')}</th>
          <th className="py-2 pr-3">{t('col.scheduled')}</th>
          <th className="py-2 pr-3">{t('col.confirmed')}</th>
          <th className="py-2 pr-3">{t('col.noShow')}</th>
          <th className="py-2">{t('col.cancelled')}</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {data.map((row) => (
          <tr key={row.providerId}>
            <td className="py-2 pr-3 font-medium text-gray-900">{row.providerName}</td>
            <td className="py-2 pr-3">{row.total}</td>
            <td className="py-2 pr-3">
              <Bar value={row.total} max={max} />
            </td>
            <td className="py-2 pr-3 text-green-700">{row.completed}</td>
            <td className="py-2 pr-3">{row.scheduled}</td>
            <td className="py-2 pr-3">{row.confirmed}</td>
            <td className="py-2 pr-3 text-red-600">{row.noShow}</td>
            <td className="py-2 text-gray-500">{row.cancelled}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ProductionReport({ from, to }: { from: string; to: string }) {
  const { t } = useTranslation('reports');
  const { data, isPending } = useDailyProduction(from, to, true);
  if (isPending) return <Spinner label={t('running')} />;
  if (!data || data.days.length === 0)
    return <p className="text-sm text-gray-500">{t('noLedgerActivity')}</p>;
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
        {[
          ['production', data.totalCharges],
          ['patientCollections', data.totalPatientPayments],
          ['insuranceCollections', data.totalInsurancePayments],
          ['adjustments', data.totalAdjustments],
          ['net', data.totalNet],
        ].map(([key, value]) => (
          <div key={key as string} className="rounded-md bg-gray-50 p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
              {t(`summary.${key}`)}
            </p>
            <p className="text-lg font-bold text-gray-900">{formatMoney(value as number)}</p>
          </div>
        ))}
      </div>
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <thead>
          <tr className="text-left text-xs font-semibold uppercase text-gray-500">
            <th className="py-2 pr-3">{t('col.date')}</th>
            <th className="py-2 pr-3 text-right">{t('col.charges')}</th>
            <th className="py-2 pr-3 text-right">{t('col.patientPmts')}</th>
            <th className="py-2 pr-3 text-right">{t('col.insurancePmts')}</th>
            <th className="py-2 pr-3 text-right">{t('col.adjustments')}</th>
            <th className="py-2 text-right">{t('col.net')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {data.days.map((day) => (
            <tr key={day.date}>
              <td className="py-2 pr-3 text-gray-600">{day.date}</td>
              <td className="py-2 pr-3 text-right">{formatMoney(day.charges)}</td>
              <td className="py-2 pr-3 text-right text-green-700">
                {formatMoney(day.patientPayments)}
              </td>
              <td className="py-2 pr-3 text-right text-blue-700">
                {formatMoney(day.insurancePayments)}
              </td>
              <td className="py-2 pr-3 text-right text-yellow-700">{formatMoney(day.adjustments)}</td>
              <td className="py-2 text-right font-medium">{formatMoney(day.net)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const entryTone: Record<DaySheetEntryType, 'red' | 'green' | 'yellow' | 'gray'> = {
  CHARGE: 'red',
  PAYMENT: 'green',
  ADJUSTMENT: 'yellow',
  REVERSAL: 'gray',
};

const entryLabel = (t: TFunction, type: DaySheetEntryType): string => t(`entryLabel.${type}`);

function DaySheetReportView({ date }: { date: string }) {
  const { t } = useTranslation('reports');
  const { data, isPending } = useDaySheet(date);
  if (isPending) return <Spinner label={t('running')} />;
  if (!data) return <p className="text-sm text-gray-500">{t('daySheet.noData')}</p>;

  return (
    <div className="print-area space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-gray-900">
          {t('daySheet.title', { date: data.date })}
        </h2>
        <Button variant="secondary" className="no-print" onClick={() => window.print()}>
          {t('print')}
        </Button>
      </div>

      <div className="grid grid-cols-3 gap-4">
        {(
          [
            ['daySheet.production', data.totals.production, 'day-sheet-production'],
            ['daySheet.collections', data.totals.collections, 'day-sheet-collections'],
            ['daySheet.adjustments', data.totals.adjustments, 'day-sheet-adjustments'],
          ] as const
        ).map(([labelKey, value, testId]) => (
          <div key={testId} className="rounded-md bg-gray-50 p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
              {t(labelKey)}
            </p>
            <p data-testid={testId} className="text-lg font-bold text-gray-900">
              {formatMoney(value)}
            </p>
          </div>
        ))}
      </div>

      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500">
          {t('daySheet.byProvider')}
        </h3>
        {data.providers.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">{t('daySheet.noProviderActivity')}</p>
        ) : (
          <table className="mt-2 min-w-full divide-y divide-gray-200 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-gray-500">
                <th className="py-2 pr-3">{t('col.provider')}</th>
                <th className="py-2 pr-3 text-right">{t('daySheet.production')}</th>
                <th className="py-2 text-right">{t('daySheet.collections')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.providers.map((row) => (
                <tr key={row.providerId}>
                  <td className="py-2 pr-3 font-medium text-gray-900">{row.providerName}</td>
                  <td className="py-2 pr-3 text-right">{formatMoney(row.production)}</td>
                  <td className="py-2 text-right text-green-700">
                    {formatMoney(row.collections)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500">
          {t('daySheet.entries')}
        </h3>
        {data.entries.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">{t('daySheet.noActivity')}</p>
        ) : (
          <table className="mt-2 min-w-full divide-y divide-gray-200 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-gray-500">
                <th className="py-2 pr-3">{t('col.time')}</th>
                <th className="py-2 pr-3">{t('col.patient')}</th>
                <th className="py-2 pr-3">{t('col.provider')}</th>
                <th className="py-2 pr-3">{t('col.type')}</th>
                <th className="py-2 pr-3">{t('col.description')}</th>
                <th className="py-2 text-right">{t('col.amount')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.entries.map((entry) => (
                <tr key={entry.entryId}>
                  <td className="py-2 pr-3 whitespace-nowrap text-gray-600">
                    {new Date(entry.occurredAt).toLocaleTimeString([], {
                      hour: 'numeric',
                      minute: '2-digit',
                    })}
                  </td>
                  <td className="py-2 pr-3 font-medium text-gray-900">{entry.patientName}</td>
                  <td className="py-2 pr-3 text-gray-600">{entry.providerName ?? '—'}</td>
                  <td className="py-2 pr-3">
                    <Badge tone={entryTone[entry.type]}>{entryLabel(t, entry.type)}</Badge>
                  </td>
                  <td className="py-2 pr-3 text-gray-600">{entry.description}</td>
                  <td
                    className={`py-2 text-right font-medium ${
                      entry.amount < 0 ? 'text-red-600' : 'text-gray-900'
                    }`}
                  >
                    {formatMoney(entry.amount)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500">
          {t('daySheet.depositSlip')}
        </h3>
        {data.depositSlip.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">{t('daySheet.noPayments')}</p>
        ) : (
          <table className="mt-2 min-w-full divide-y divide-gray-200 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-gray-500">
                <th className="py-2 pr-3">{t('col.method')}</th>
                <th className="py-2 pr-3 text-right">{t('col.count')}</th>
                <th className="py-2 text-right">{t('col.total')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.depositSlip.map((row) => (
                <tr key={row.method}>
                  <td className="py-2 pr-3 font-medium text-gray-900">{row.method}</td>
                  <td className="py-2 pr-3 text-right">{row.count}</td>
                  <td className="py-2 text-right font-medium">{formatMoney(row.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function GrowthReport() {
  const { t } = useTranslation('reports');
  const { data, isPending } = usePatientGrowth(12);
  if (isPending) return <Spinner label={t('running')} />;
  if (!data || data.length === 0)
    return <p className="text-sm text-gray-500">{t('noPatients')}</p>;
  const max = Math.max(...data.map((r) => r.newPatients));
  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">{t('col.month')}</th>
          <th className="py-2 pr-3">{t('col.newPatients')}</th>
          <th className="py-2 pr-3" />
          <th className="py-2">{t('col.cumulative')}</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {data.map((row) => (
          <tr key={row.month}>
            <td className="py-2 pr-3 font-medium text-gray-900">{row.month}</td>
            <td className="py-2 pr-3">{row.newPatients}</td>
            <td className="py-2 pr-3">
              <Bar value={row.newPatients} max={max} color="#16a34a" />
            </td>
            <td className="py-2 text-gray-600">{row.cumulative}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function UtilizationReport({ from, to }: { from: string; to: string }) {
  const { t } = useTranslation('reports');
  const { data, isPending } = useProviderUtilization(from, to);
  if (isPending) return <Spinner label={t('running')} />;
  if (!data || data.length === 0)
    return <p className="text-sm text-gray-500">{t('noBookedTime')}</p>;
  const max = Math.max(...data.map((r) => r.bookedMinutes));
  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">{t('col.provider')}</th>
          <th className="py-2 pr-3">{t('col.appointments')}</th>
          <th className="py-2 pr-3">{t('col.booked')}</th>
          <th className="py-2 pr-3" />
          <th className="py-2 pr-3">{t('col.completedTime')}</th>
          <th className="py-2">{t('col.patients')}</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {data.map((row) => (
          <tr key={row.providerId}>
            <td className="py-2 pr-3 font-medium text-gray-900">{row.providerName}</td>
            <td className="py-2 pr-3">{row.appointments}</td>
            <td className="py-2 pr-3">
              {t('hours', { value: (row.bookedMinutes / 60).toFixed(1) })}
            </td>
            <td className="py-2 pr-3">
              <Bar value={row.bookedMinutes} max={max} color="#7c3aed" />
            </td>
            <td className="py-2 pr-3">{t('hours', { value: (row.completedMinutes / 60).toFixed(1) })}</td>
            <td className="py-2">{row.distinctPatients}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// ---- A/R aging ----

type ArSortKey =
  | 'guarantorName'
  | 'current'
  | 'days30'
  | 'days60'
  | 'days90plus'
  | 'total'
  | 'lastPaymentDate';

const AR_COLUMNS: Array<{ key: ArSortKey; labelKey: string; numeric: boolean }> = [
  { key: 'guarantorName', labelKey: 'col.guarantor', numeric: false },
  { key: 'current', labelKey: 'col.current', numeric: true },
  { key: 'days30', labelKey: 'col.days30', numeric: true },
  { key: 'days60', labelKey: 'col.days60', numeric: true },
  { key: 'days90plus', labelKey: 'col.days90plus', numeric: true },
  { key: 'total', labelKey: 'col.total', numeric: true },
  { key: 'lastPaymentDate', labelKey: 'col.lastPayment', numeric: false },
];

const AR_BUCKETS = [
  ['current', 'col.current'],
  ['days30', 'col.days30'],
  ['days60', 'col.days60'],
  ['days90plus', 'col.days90plus'],
  ['total', 'col.total'],
] as const;

function compareAr(a: ArAgingRow, b: ArAgingRow, key: ArSortKey): number {
  const av = a[key];
  const bv = b[key];
  if (av == null && bv == null) return 0;
  if (av == null) return -1;
  if (bv == null) return 1;
  if (typeof av === 'number' && typeof bv === 'number') return av - bv;
  return String(av).localeCompare(String(bv));
}

function ArAgingReportView() {
  const { t } = useTranslation('reports');
  const { data, isPending } = useArAging();
  const [sortKey, setSortKey] = useState<ArSortKey>('total');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');

  const rows = useMemo(() => {
    const sorted = [...(data?.rows ?? [])].sort((a, b) => compareAr(a, b, sortKey));
    return sortDir === 'desc' ? sorted.reverse() : sorted;
  }, [data, sortKey, sortDir]);

  if (isPending) return <Spinner label={t('running')} />;
  if (!data) return <p className="text-sm text-gray-500">{t('ar.noData')}</p>;

  const toggleSort = (key: ArSortKey) => {
    if (key === sortKey) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir(key === 'guarantorName' ? 'asc' : 'desc');
    }
  };

  return (
    <div className="print-area space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-gray-900">{t('ar.title')}</h2>
        <Button variant="secondary" className="no-print" onClick={() => window.print()}>
          {t('print')}
        </Button>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
        {AR_BUCKETS.map(([key, labelKey]) => (
          <div key={key} className="rounded-md bg-gray-50 p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
              {t(labelKey)}
            </p>
            <p data-testid={`ar-bucket-${key}`} className="text-lg font-bold text-gray-900">
              {formatMoney(data.buckets[key])}
            </p>
          </div>
        ))}
      </div>

      {rows.length === 0 ? (
        <p className="text-sm text-gray-500">{t('ar.noBalances')}</p>
      ) : (
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead>
            <tr className="text-xs font-semibold uppercase text-gray-500">
              <th className="py-2 pr-3 text-left">
                <SortHeader column={AR_COLUMNS[0]} sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} />
              </th>
              <th className="py-2 pr-3 text-left">{t('col.phone')}</th>
              {AR_COLUMNS.slice(1, 6).map((column) => (
                <th key={column.key} className="py-2 pr-3 text-right">
                  <SortHeader column={column} sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} />
                </th>
              ))}
              <th className="py-2 text-left">
                <SortHeader column={AR_COLUMNS[6]} sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} />
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rows.map((row) => (
              <tr key={row.guarantorId}>
                <td className="py-2 pr-3">
                  <Link
                    to={`/patients/${row.guarantorId}`}
                    className="font-medium text-brand-700 hover:underline"
                  >
                    {row.guarantorName}
                  </Link>
                </td>
                <td className="py-2 pr-3 whitespace-nowrap text-gray-600">{row.phone ?? '—'}</td>
                <td className="py-2 pr-3 text-right">{formatMoney(row.current)}</td>
                <td className="py-2 pr-3 text-right">{formatMoney(row.days30)}</td>
                <td className="py-2 pr-3 text-right">{formatMoney(row.days60)}</td>
                <td className="py-2 pr-3 text-right text-red-600">{formatMoney(row.days90plus)}</td>
                <td className="py-2 pr-3 text-right font-semibold">{formatMoney(row.total)}</td>
                <td className="py-2 text-gray-600">{row.lastPaymentDate ?? t('ar.never')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function SortHeader({
  column,
  sortKey,
  sortDir,
  onSort,
}: {
  column: { key: ArSortKey; labelKey: string };
  sortKey: ArSortKey;
  sortDir: 'asc' | 'desc';
  onSort: (key: ArSortKey) => void;
}) {
  const { t } = useTranslation('reports');
  const active = sortKey === column.key;
  return (
    <button
      type="button"
      onClick={() => onSort(column.key)}
      className={`uppercase hover:text-gray-700 ${active ? 'text-gray-900' : ''}`}
    >
      {t(column.labelKey)}
      {active && <span aria-hidden> {sortDir === 'asc' ? '▲' : '▼'}</span>}
    </button>
  );
}

// ---- Collections ----

function CollectionsReportView() {
  const { t } = useTranslation('reports');
  const { data, isPending } = useCollections();
  if (isPending) return <Spinner label={t('running')} />;
  if (!data || data.length === 0)
    return <p className="text-sm text-gray-500">{t('collections.none')}</p>;
  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm" data-testid="collections-table">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">{t('col.guarantor')}</th>
          <th className="py-2 pr-3">{t('col.phone')}</th>
          <th className="py-2 pr-3 text-right">{t('col.overdue')}</th>
          <th className="py-2 pr-3">{t('col.lastPayment')}</th>
          <th className="py-2">{t('col.oldestCharge')}</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {data.map((row) => (
          <tr key={row.guarantorId}>
            <td className="py-2 pr-3">
              <Link
                to={`/patients/${row.guarantorId}`}
                className="font-medium text-brand-700 hover:underline"
              >
                {row.guarantorName}
              </Link>
            </td>
            <td className="py-2 pr-3 whitespace-nowrap text-gray-600">{row.phone ?? '—'}</td>
            <td className="py-2 pr-3 text-right font-semibold text-red-600">
              {formatMoney(row.totalOverdue)}
            </td>
            <td className="py-2 pr-3 text-gray-600">{row.lastPaymentDate ?? t('collections.never')}</td>
            <td className="py-2 text-gray-600">{row.oldestChargeDate}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
