import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { PageResponse, Provider } from '../../types/api';

export interface ProviderInput {
  type: string;
  firstName: string;
  lastName: string;
  npi?: string | null;
  specialty?: string | null;
  licenseNumber?: string | null;
  licenseState?: string | null;
  email?: string | null;
  phone?: string | null;
  color: string;
  active: boolean;
}

export function useProviders(includeInactive: boolean) {
  return useQuery({
    queryKey: ['providers', { includeInactive }],
    queryFn: () =>
      api<PageResponse<Provider>>(
        `/api/v1/providers?includeInactive=${includeInactive}&size=100`,
      ),
  });
}

export function useCreateProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ProviderInput) =>
      api<Provider>('/api/v1/providers', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['providers'] }),
  });
}

export function useUpdateProvider(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ProviderInput) =>
      api<Provider>(`/api/v1/providers/${id}`, { method: 'PUT', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['providers'] }),
  });
}

// ---- availability ----

export interface HoursBlock {
  dayOfWeek: number; // ISO 1=Mon .. 7=Sun
  startTime: string; // HH:mm[:ss]
  endTime: string;
}

export interface TimeOff {
  id: string;
  startsAt: string;
  endsAt: string;
  reason: string | null;
}

export function useProviderHours(providerId: string | null) {
  return useQuery({
    queryKey: ['provider-hours', providerId],
    queryFn: () => api<HoursBlock[]>(`/api/v1/providers/${providerId}/hours`),
    enabled: providerId !== null,
  });
}

export function useReplaceProviderHours(providerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (blocks: HoursBlock[]) =>
      api<HoursBlock[]>(`/api/v1/providers/${providerId}/hours`, {
        method: 'PUT',
        body: blocks,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['provider-hours'] }),
  });
}

export function useProviderTimeOff(providerId: string | null) {
  return useQuery({
    queryKey: ['provider-time-off', providerId],
    queryFn: () => api<TimeOff[]>(`/api/v1/providers/${providerId}/time-off`),
    enabled: providerId !== null,
  });
}

export function useAddTimeOff(providerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { startsAt: string; endsAt: string; reason?: string }) =>
      api<TimeOff>(`/api/v1/providers/${providerId}/time-off`, { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['provider-time-off'] }),
  });
}

export function useRemoveTimeOff(providerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (timeOffId: string) =>
      api<void>(`/api/v1/providers/${providerId}/time-off/${timeOffId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['provider-time-off'] }),
  });
}
