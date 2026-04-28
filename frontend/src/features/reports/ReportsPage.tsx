import { useState } from 'react';
import { Spinner } from '../../components/Spinner';
import { useAuth } from '../../lib/auth';
import {
  useAppointmentsByProvider,
  useDailyProduction,
  usePatientGrowth,
  useProviderUtilization,
} from './api';

const REPORTS = [
  'Appointments by provider',
  'Daily production',
  'Patient growth',
  'Provider utilization',
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
  // READ_ONLY cannot fetch any report endpoint, so don't render the page at all
  const canSeeReports = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK', 'BILLING');
  const reports = REPORTS.filter((r) => r !== 'Daily production' || canSeeFinancials);

  const [report, setReport] = useState<Report>(reports[0]);
  const [from, setFrom] = useState(isoDaysAgo(30));
  const [to, setTo] = useState(isoDaysAgo(0));

  const showRange = report !== 'Patient growth';

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
        {report === 'Patient growth' && <GrowthReport />}
        {report === 'Provider utilization' && <UtilizationReport from={from} to={to} />}
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
