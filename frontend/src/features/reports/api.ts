import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';

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
