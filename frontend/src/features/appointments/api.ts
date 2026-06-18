import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type {
  Appointment,
  AppointmentStatus,
  Blockout,
  Operatory,
  RecurrenceFrequency,
  RecurringResult,
} from '../../types/api';

export interface AppointmentInput {
  patientId: string;
  providerId: string;
  operatoryId: string;
  startsAt: string;
  endsAt: string;
  asap?: boolean;
  notes?: string | null;
  colorOverride?: string | null;
}

export function useAppointments(from: string, to: string) {
  return useQuery({
    queryKey: ['appointments', { from, to }],
    queryFn: () =>
      api<Appointment[]>(
        `/api/v1/appointments?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
      ),
    placeholderData: (previous) => previous,
  });
}

export function usePatientAppointments(patientId: string) {
  // Wide fixed window so the patient's full history (and future bookings) load.
  const from = '2000-01-01T00:00:00Z';
  const to = new Date(Date.now() + 2 * 365 * 24 * 3600 * 1000).toISOString();
  return useQuery({
    queryKey: ['appointments', { patientId }],
    queryFn: () =>
      api<Appointment[]>(
        `/api/v1/appointments?from=${from}&to=${encodeURIComponent(to)}&patientId=${patientId}`,
      ),
  });
}

export function useCreateAppointment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: AppointmentInput) =>
      api<Appointment>('/api/v1/appointments', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
  });
}

export function useUpdateAppointment(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: AppointmentInput) =>
      api<Appointment>(`/api/v1/appointments/${id}`, { method: 'PUT', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
  });
}

export function useUpdateAppointmentStatus(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { status: AppointmentStatus; cancelReason?: string }) =>
      api<Appointment>(`/api/v1/appointments/${id}/status`, { method: 'PATCH', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
  });
}

export function useRescheduleAppointment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: AppointmentInput }) =>
      api<Appointment>(`/api/v1/appointments/${id}`, { method: 'PUT', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
  });
}

export function useCreateRecurring() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      base: AppointmentInput;
      frequency: RecurrenceFrequency;
      occurrences: number;
    }) => api<RecurringResult>('/api/v1/appointments/recurring', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
  });
}

export function useSendConfirmation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api<Appointment>(`/api/v1/appointments/${id}/send-confirmation`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
  });
}

export function useBlockouts(from: string, to: string) {
  return useQuery({
    queryKey: ['blockouts', from, to],
    queryFn: () =>
      api<Blockout[]>(
        `/api/v1/blockouts?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
      ),
  });
}

export function useCreateBlockout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      operatoryId: string;
      startsAt: string;
      endsAt: string;
      reason?: string;
    }) => api<Blockout>('/api/v1/blockouts', { method: 'POST', body: input }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['blockouts'] });
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
    },
  });
}

export function useDeleteBlockout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api<void>(`/api/v1/blockouts/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['blockouts'] }),
  });
}

export function useOperatories(includeInactive = false) {
  return useQuery({
    queryKey: ['operatories', { includeInactive }],
    queryFn: () => api<Operatory[]>(`/api/v1/operatories?includeInactive=${includeInactive}`),
  });
}

export function useCreateOperatory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string }) =>
      api<Operatory>('/api/v1/operatories', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['operatories'] }),
  });
}

export function useUpdateOperatory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...input }: { id: string; name: string; active: boolean }) =>
      api<Operatory>(`/api/v1/operatories/${id}`, { method: 'PUT', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['operatories'] }),
  });
}

export const NEXT_STATUSES: Record<AppointmentStatus, AppointmentStatus[]> = {
  SCHEDULED: ['CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW'],
  CONFIRMED: ['CHECKED_IN', 'CANCELLED', 'NO_SHOW'],
  CHECKED_IN: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['COMPLETED'],
  COMPLETED: [],
  NO_SHOW: [],
  CANCELLED: [],
};

export const STATUS_LABELS: Record<AppointmentStatus, string> = {
  SCHEDULED: 'Scheduled',
  CONFIRMED: 'Confirmed',
  CHECKED_IN: 'Checked in',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  NO_SHOW: 'No-show',
  CANCELLED: 'Cancelled',
};
