import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
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

const money = (n: number) => `$${n.toFixed(2)}`;

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

export function ReportsPage() {
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
        You do not have permission to view reports.
      </div>
    );
  }

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900">Reports</h1>

      <div className="mt-4 flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor="report-select" className="block text-sm font-medium text-gray-700">
            Report
          </label>
          <select
            id="report-select"
            value={report}
            onChange={(e) => setReport(e.target.value as Report)}
            className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          >
            {reports.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
        </div>
        {report === 'Day sheet' && (
          <div>
            <label htmlFor="report-date" className="block text-sm font-medium text-gray-700">
              Date
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
                From
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
                To
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
  const { data, isPending } = useAppointmentsByProvider(from, to);
  if (isPending) return <Spinner label="Running report…" />;
  if (!data || data.length === 0)
    return <p className="text-sm text-gray-500">No appointments in this range.</p>;
  const max = Math.max(...data.map((r) => r.total));
  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">Provider</th>
          <th className="py-2 pr-3">Total</th>
          <th className="py-2 pr-3" />
          <th className="py-2 pr-3">Completed</th>
          <th className="py-2 pr-3">Scheduled</th>
          <th className="py-2 pr-3">Confirmed</th>
          <th className="py-2 pr-3">No-show</th>
          <th className="py-2">Cancelled</th>
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
  const { data, isPending } = useDailyProduction(from, to, true);
  if (isPending) return <Spinner label="Running report…" />;
  if (!data || data.days.length === 0)
    return <p className="text-sm text-gray-500">No ledger activity in this range.</p>;
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
        {[
          ['Production', data.totalCharges],
          ['Patient collections', data.totalPatientPayments],
          ['Insurance collections', data.totalInsurancePayments],
          ['Adjustments', data.totalAdjustments],
          ['Net', data.totalNet],
        ].map(([label, value]) => (
          <div key={label as string} className="rounded-md bg-gray-50 p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
              {label}
            </p>
            <p className="text-lg font-bold text-gray-900">{money(value as number)}</p>
          </div>
        ))}
      </div>
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <thead>
          <tr className="text-left text-xs font-semibold uppercase text-gray-500">
            <th className="py-2 pr-3">Date</th>
            <th className="py-2 pr-3 text-right">Charges</th>
            <th className="py-2 pr-3 text-right">Patient pmts</th>
            <th className="py-2 pr-3 text-right">Insurance pmts</th>
            <th className="py-2 pr-3 text-right">Adjustments</th>
            <th className="py-2 text-right">Net</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {data.days.map((day) => (
            <tr key={day.date}>
              <td className="py-2 pr-3 text-gray-600">{day.date}</td>
              <td className="py-2 pr-3 text-right">{money(day.charges)}</td>
              <td className="py-2 pr-3 text-right text-green-700">
                {money(day.patientPayments)}
              </td>
              <td className="py-2 pr-3 text-right text-blue-700">
                {money(day.insurancePayments)}
              </td>
              <td className="py-2 pr-3 text-right text-yellow-700">{money(day.adjustments)}</td>
              <td className="py-2 text-right font-medium">{money(day.net)}</td>
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

const entryLabel: Record<DaySheetEntryType, string> = {
  CHARGE: 'Charge',
  PAYMENT: 'Payment',
  ADJUSTMENT: 'Adjustment',
  REVERSAL: 'Reversal',
};

const signedMoney = (n: number) => `${n < 0 ? '−' : ''}$${Math.abs(n).toFixed(2)}`;

function DaySheetReportView({ date }: { date: string }) {
  const { data, isPending } = useDaySheet(date);
  if (isPending) return <Spinner label="Running report…" />;
  if (!data) return <p className="text-sm text-gray-500">No day sheet for this date.</p>;

  return (
    <div className="print-area space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-gray-900">Day sheet — {data.date}</h2>
        <Button variant="secondary" className="no-print" onClick={() => window.print()}>
          Print
        </Button>
      </div>

      <div className="grid grid-cols-3 gap-4">
        {(
          [
            ['Production', data.totals.production, 'day-sheet-production'],
            ['Collections', data.totals.collections, 'day-sheet-collections'],
            ['Adjustments', data.totals.adjustments, 'day-sheet-adjustments'],
          ] as const
        ).map(([label, value, testId]) => (
          <div key={label} className="rounded-md bg-gray-50 p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">{label}</p>
            <p data-testid={testId} className="text-lg font-bold text-gray-900">
              {signedMoney(value)}
            </p>
          </div>
        ))}
      </div>

      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500">
          By provider
        </h3>
        {data.providers.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">No provider activity.</p>
        ) : (
          <table className="mt-2 min-w-full divide-y divide-gray-200 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-gray-500">
                <th className="py-2 pr-3">Provider</th>
                <th className="py-2 pr-3 text-right">Production</th>
                <th className="py-2 text-right">Collections</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.providers.map((row) => (
                <tr key={row.providerId}>
                  <td className="py-2 pr-3 font-medium text-gray-900">{row.providerName}</td>
                  <td className="py-2 pr-3 text-right">{signedMoney(row.production)}</td>
                  <td className="py-2 text-right text-green-700">
                    {signedMoney(row.collections)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500">Entries</h3>
        {data.entries.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">No activity on this date.</p>
        ) : (
          <table className="mt-2 min-w-full divide-y divide-gray-200 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-gray-500">
                <th className="py-2 pr-3">Time</th>
                <th className="py-2 pr-3">Patient</th>
                <th className="py-2 pr-3">Provider</th>
                <th className="py-2 pr-3">Type</th>
                <th className="py-2 pr-3">Description</th>
                <th className="py-2 text-right">Amount</th>
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
                    <Badge tone={entryTone[entry.type]}>{entryLabel[entry.type]}</Badge>
                  </td>
                  <td className="py-2 pr-3 text-gray-600">{entry.description}</td>
                  <td
                    className={`py-2 text-right font-medium ${
                      entry.amount < 0 ? 'text-red-600' : 'text-gray-900'
                    }`}
                  >
                    {signedMoney(entry.amount)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500">
          Deposit slip
        </h3>
        {data.depositSlip.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">No payments collected.</p>
        ) : (
          <table className="mt-2 min-w-full divide-y divide-gray-200 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-gray-500">
                <th className="py-2 pr-3">Method</th>
                <th className="py-2 pr-3 text-right">Count</th>
                <th className="py-2 text-right">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.depositSlip.map((row) => (
                <tr key={row.method}>
                  <td className="py-2 pr-3 font-medium text-gray-900">{row.method}</td>
                  <td className="py-2 pr-3 text-right">{row.count}</td>
                  <td className="py-2 text-right font-medium">{signedMoney(row.total)}</td>
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
  const { data, isPending } = usePatientGrowth(12);
  if (isPending) return <Spinner label="Running report…" />;
  if (!data || data.length === 0)
    return <p className="text-sm text-gray-500">No patients yet.</p>;
  const max = Math.max(...data.map((r) => r.newPatients));
  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">Month</th>
          <th className="py-2 pr-3">New patients</th>
          <th className="py-2 pr-3" />
          <th className="py-2">Cumulative</th>
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
  const { data, isPending } = useProviderUtilization(from, to);
  if (isPending) return <Spinner label="Running report…" />;
  if (!data || data.length === 0)
    return <p className="text-sm text-gray-500">No booked time in this range.</p>;
  const max = Math.max(...data.map((r) => r.bookedMinutes));
  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">Provider</th>
          <th className="py-2 pr-3">Appointments</th>
          <th className="py-2 pr-3">Booked</th>
          <th className="py-2 pr-3" />
          <th className="py-2 pr-3">Completed time</th>
          <th className="py-2">Patients</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {data.map((row) => (
          <tr key={row.providerId}>
            <td className="py-2 pr-3 font-medium text-gray-900">{row.providerName}</td>
            <td className="py-2 pr-3">{row.appointments}</td>
            <td className="py-2 pr-3">
              {(row.bookedMinutes / 60).toFixed(1)}h
            </td>
            <td className="py-2 pr-3">
              <Bar value={row.bookedMinutes} max={max} color="#7c3aed" />
            </td>
            <td className="py-2 pr-3">{(row.completedMinutes / 60).toFixed(1)}h</td>
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

const AR_COLUMNS: Array<{ key: ArSortKey; label: string; numeric: boolean }> = [
  { key: 'guarantorName', label: 'Guarantor', numeric: false },
  { key: 'current', label: 'Current', numeric: true },
  { key: 'days30', label: '30 days', numeric: true },
  { key: 'days60', label: '60 days', numeric: true },
  { key: 'days90plus', label: '90+ days', numeric: true },
  { key: 'total', label: 'Total', numeric: true },
  { key: 'lastPaymentDate', label: 'Last payment', numeric: false },
];

const AR_BUCKETS = [
  ['current', 'Current'],
  ['days30', '30 days'],
  ['days60', '60 days'],
  ['days90plus', '90+ days'],
  ['total', 'Total'],
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
  const { data, isPending } = useArAging();
  const [sortKey, setSortKey] = useState<ArSortKey>('total');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');

  const rows = useMemo(() => {
    const sorted = [...(data?.rows ?? [])].sort((a, b) => compareAr(a, b, sortKey));
    return sortDir === 'desc' ? sorted.reverse() : sorted;
  }, [data, sortKey, sortDir]);

  if (isPending) return <Spinner label="Running report…" />;
  if (!data) return <p className="text-sm text-gray-500">No A/R data.</p>;

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
        <h2 className="text-lg font-semibold text-gray-900">Accounts receivable aging</h2>
        <Button variant="secondary" className="no-print" onClick={() => window.print()}>
          Print
        </Button>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
        {AR_BUCKETS.map(([key, label]) => (
          <div key={key} className="rounded-md bg-gray-50 p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
              {label}
            </p>
            <p data-testid={`ar-bucket-${key}`} className="text-lg font-bold text-gray-900">
              {money(data.buckets[key])}
            </p>
          </div>
        ))}
      </div>

      {rows.length === 0 ? (
        <p className="text-sm text-gray-500">No outstanding balances.</p>
      ) : (
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead>
            <tr className="text-xs font-semibold uppercase text-gray-500">
              <th className="py-2 pr-3 text-left">
                <SortHeader column={AR_COLUMNS[0]} sortKey={sortKey} sortDir={sortDir} onSort={toggleSort} />
              </th>
              <th className="py-2 pr-3 text-left">Phone</th>
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
                <td className="py-2 pr-3 text-right">{money(row.current)}</td>
                <td className="py-2 pr-3 text-right">{money(row.days30)}</td>
                <td className="py-2 pr-3 text-right">{money(row.days60)}</td>
                <td className="py-2 pr-3 text-right text-red-600">{money(row.days90plus)}</td>
                <td className="py-2 pr-3 text-right font-semibold">{money(row.total)}</td>
                <td className="py-2 text-gray-600">{row.lastPaymentDate ?? 'Never'}</td>
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
  column: { key: ArSortKey; label: string };
  sortKey: ArSortKey;
  sortDir: 'asc' | 'desc';
  onSort: (key: ArSortKey) => void;
}) {
  const active = sortKey === column.key;
  return (
    <button
      type="button"
      onClick={() => onSort(column.key)}
      className={`uppercase hover:text-gray-700 ${active ? 'text-gray-900' : ''}`}
    >
      {column.label}
      {active && <span aria-hidden> {sortDir === 'asc' ? '▲' : '▼'}</span>}
    </button>
  );
}

// ---- Collections ----

function CollectionsReportView() {
  const { data, isPending } = useCollections();
  if (isPending) return <Spinner label="Running report…" />;
  if (!data || data.length === 0)
    return <p className="text-sm text-gray-500">No overdue accounts. Nice work.</p>;
  return (
    <table className="min-w-full divide-y divide-gray-200 text-sm" data-testid="collections-table">
      <thead>
        <tr className="text-left text-xs font-semibold uppercase text-gray-500">
          <th className="py-2 pr-3">Guarantor</th>
          <th className="py-2 pr-3">Phone</th>
          <th className="py-2 pr-3 text-right">Overdue</th>
          <th className="py-2 pr-3">Last payment</th>
          <th className="py-2">Oldest charge</th>
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
              {money(row.totalOverdue)}
            </td>
            <td className="py-2 pr-3 text-gray-600">{row.lastPaymentDate ?? 'Never'}</td>
            <td className="py-2 text-gray-600">{row.oldestChargeDate}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
