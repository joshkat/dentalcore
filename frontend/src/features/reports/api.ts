import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { AppointmentStatus, TreatmentPlanStatus } from '../../types/api';

export interface ProviderAppointmentsRow {
  providerId: string;
  providerName: string;
  scheduled: number;
  confirmed: number;
  checkedIn: number;
  inProgress: number;
  completed: number;
  noShow: number;
  cancelled: number;
  total: number;
}

export interface DailyProductionRow {
  date: string;
  charges: number;
  patientPayments: number;
  insurancePayments: number;
  adjustments: number;
  net: number;
}

export interface DailyProductionReport {
  days: DailyProductionRow[];
  totalCharges: number;
  totalPatientPayments: number;
  totalInsurancePayments: number;
  totalAdjustments: number;
  totalNet: number;
}

export interface PatientGrowthRow {
  month: string;
  newPatients: number;
  cumulative: number;
}

export interface ProviderUtilizationRow {
  providerId: string;
  providerName: string;
  appointments: number;
  bookedMinutes: number;
  completedMinutes: number;
  distinctPatients: number;
}

export interface DashboardSummary {
  activePatients: number;
  todaysAppointments: number;
  todaysCompletedAppointments: number;
  todaysProduction: number;
  todaysCollections: number;
  openClaims: number;
}

export function useDashboard() {
  return useQuery({
    queryKey: ['reports', 'dashboard'],
    queryFn: () => api<DashboardSummary>('/api/v1/reports/dashboard'),
    refetchOnWindowFocus: true,
  });
}

export function useAppointmentsByProvider(from: string, to: string) {
  return useQuery({
    queryKey: ['reports', 'appointments-by-provider', { from, to }],
    queryFn: () =>
      api<ProviderAppointmentsRow[]>(
        `/api/v1/reports/appointments-by-provider?from=${from}&to=${to}`,
      ),
  });
}

export function useDailyProduction(from: string, to: string, enabled: boolean) {
  return useQuery({
    queryKey: ['reports', 'daily-production', { from, to }],
    queryFn: () =>
      api<DailyProductionReport>(`/api/v1/reports/daily-production?from=${from}&to=${to}`),
    enabled,
  });
}

export function usePatientGrowth(months: number) {
  return useQuery({
    queryKey: ['reports', 'patient-growth', { months }],
    queryFn: () => api<PatientGrowthRow[]>(`/api/v1/reports/patient-growth?months=${months}`),
  });
}

export function useProviderUtilization(from: string, to: string) {
  return useQuery({
    queryKey: ['reports', 'provider-utilization', { from, to }],
    queryFn: () =>
      api<ProviderUtilizationRow[]>(
        `/api/v1/reports/provider-utilization?from=${from}&to=${to}`,
      ),
  });
}

// ---- day sheet ----

export type DaySheetEntryType = 'CHARGE' | 'PAYMENT' | 'ADJUSTMENT' | 'REVERSAL';

export interface DaySheetProviderRow {
  providerId: string;
  providerName: string;
  production: number;
  collections: number;
}

export interface DaySheetEntry {
  entryId: string;
  occurredAt: string;
  patientId: string;
  patientName: string;
  providerName: string | null;
  type: DaySheetEntryType;
  description: string;
  amount: number;
}

export interface DaySheetReport {
  date: string;
  providers: DaySheetProviderRow[];
  entries: DaySheetEntry[];
  totals: {
    production: number;
    collections: number;
    adjustments: number;
  };
  depositSlip: Array<{ method: string; count: number; total: number }>;
}

export function useDaySheet(date: string, enabled = true) {
  return useQuery({
    queryKey: ['reports', 'day-sheet', { date }],
    queryFn: () => api<DaySheetReport>(`/api/v1/reports/day-sheet?date=${date}`),
    enabled,
  });
}

// ---- worklists ----

export interface UnscheduledTreatmentRow {
  patientId: string;
  patientName: string;
  phone: string | null;
  planId: string;
  planTitle: string;
  planStatus: TreatmentPlanStatus;
  plannedCount: number;
  remainingValue: number;
  nextRecallDate: string | null;
}

export function useUnscheduledTreatment() {
  return useQuery({
    queryKey: ['reports', 'unscheduled-treatment'],
    queryFn: () => api<UnscheduledTreatmentRow[]>('/api/v1/reports/unscheduled-treatment'),
  });
}

export interface AsapListRow {
  appointmentId: string;
  patientId: string;
  patientName: string;
  phone: string | null;
  providerName: string;
  startsAt: string;
  status: AppointmentStatus;
}

export function useAsapList() {
  return useQuery({
    queryKey: ['reports', 'asap-list'],
    queryFn: () => api<AsapListRow[]>('/api/v1/reports/asap-list'),
  });
}
